#!/usr/bin/env python
# coding: utf-8

# In[1]:


import os
import pyspark
from pyspark.sql import SparkSession
from pyspark.sql.functions import *
from pyspark.sql.types import *


# In[2]:


KAFKA_TOPIC_NAME = "producedEvents"
KAFKA_TOPIC_NAME_OUT = "availableDBEntries"
KAFKA_BOOTSTRAP_SERVER = os.environ.get("BOOTSTRAP_SERVERS", "172.20.0.3:9092")
SPARK_MASTER_URL = "spark://spark:7077"
TIME_WINDOW = os.environ.get("PRODUCER_DATA_SEC_PER_REAL_SEC", "1")+" seconds"
TIME_TRIGGER = "1 second"
print(os.environ.get("PRODUCER_DATA_SEC_PER_REAL_SEC", ""))


# In[3]:


os.environ['PYSPARK_SUBMIT_ARGS'] = '--packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.3.1,org.postgresql:postgresql:42.5.1  pyspark-shell'
print(os.environ.get("PYSPARK_SUBMIT_ARGS", ""))


# In[4]:


sparkSession = SparkSession \
        .builder \
        .master(SPARK_MASTER_URL) \
        .appName("Spark Streaming") \
        .getOrCreate()

sparkSession.sparkContext.setLogLevel("ERROR")


# In[5]:


inDf = (
        sparkSession.readStream.format("kafka")
        .option("kafka.bootstrap.servers", KAFKA_BOOTSTRAP_SERVER)
        .option("subscribe", KAFKA_TOPIC_NAME)
        .option("startingOffsets", "latest")
        .option("includeTimestamp", True)
        .load()
    )


# In[6]:


valueDf = inDf.selectExpr("CAST(value as STRING)")

spl = split(valueDf['value'], ',')
baseDf = valueDf.withColumn('eventTime', spl.getItem(0)) \
             .withColumn('eventType', spl.getItem(1)) \
             .withColumn('productID', spl.getItem(2)) \
             .withColumn('categoryID', spl.getItem(3)) \
             .withColumn('brand', spl.getItem(5)) \
             .withColumn('price', spl.getItem(6)) \
             .withColumn('userId', spl.getItem(7)) \
             .drop('value')
baseDf = baseDf.withColumn("eventTime", regexp_replace("eventTime", "\"", "")) \
                .withColumn("eventTime", regexp_replace("eventTime", " UTC", ".000")) \
                .withColumn("eventTime", to_timestamp("eventTime"))

baseDf = baseDf.withColumn("price", regexp_replace("price", "\.", ""))
baseDf = baseDf.withColumn("price", baseDf["price"].cast(IntegerType()))


# In[7]:


queryDf = baseDf.withWatermark("eventTime", TIME_WINDOW) \
    .groupBy(window(baseDf.eventTime, TIME_WINDOW,TIME_WINDOW)) \
    .agg(count(baseDf.eventType).alias('nr_of_events'),
         count(when(baseDf.eventType == 'view', baseDf.productID)).alias('nr_items_viewed'),
         count(when(baseDf.eventType == 'cart', baseDf.productID)).alias('nr_items_put_in_cart'),
         count(when(baseDf.eventType == 'purchase', baseDf.productID)).alias('nr_items_sold'),
         sum(when(baseDf.eventType == 'purchase', baseDf.price)).alias('value_items_sold_in_cent'),
         approx_count_distinct(baseDf.userId).alias('nr_users_active'),
         approx_count_distinct(when(baseDf.eventType == 'view', baseDf.categoryID)).alias('nr_categories_viewed'),
         approx_count_distinct(when((baseDf.eventType == 'view') & (baseDf.brand != ''), baseDf.brand)).alias('nr_brands_viewed')
        )\
     .na.fill(value=0)



# In[8]:


def postgres_sink(data_frame, batch_id):
    dbname = 'postgres'
    dbuser = 'postgres'
    dbpass = 'postgres'
    dbhost = '172.20.0.6'
    dbport = '5432'

    url = "jdbc:postgresql://"+dbhost+":"+dbport+"/"+dbname
    properties = {
        "driver": "org.postgresql.Driver",
        "user": dbuser,
        "password": dbpass
    }
    df = data_frame.withColumn("start_event_time",data_frame['window'].start).withColumn("end_event_time",data_frame['window'].end)
    df = df.drop('window')
    df = df.select("start_event_time","end_event_time","nr_of_events","nr_items_viewed","nr_items_put_in_cart","nr_items_sold","value_items_sold_in_cent","nr_users_active","nr_categories_viewed","nr_brands_viewed")
    df.persist()
    df.write.jdbc(url=url, table="events", mode="append", properties=properties)
    df.select(to_json(struct("start_event_time", "end_event_time", "nr_of_events")).alias("value")) \
      .write \
      .format("kafka") \
      .option("kafka.bootstrap.servers", KAFKA_BOOTSTRAP_SERVER) \
      .option("topic", KAFKA_TOPIC_NAME_OUT) \
      .save()
    
    df.unpersist()



# In[ ]:


query = queryDf \
    .writeStream \
    .outputMode("update") \
    .option("truncate", "true")\
    .foreachBatch(postgres_sink)\
    .trigger(processingTime = TIME_TRIGGER)\
    .start()


query.awaitTermination()

    #.outputMode("update") \
    #.format("console") \


# In[ ]:




