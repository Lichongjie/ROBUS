package edu.duke.cacheplanner.algorithm

import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.mutableSeqAsJavaList
import scala.collection.JavaConverters.bufferAsJavaListConverter
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.Map

import edu.duke.cacheplanner.algorithm.singlecolumn.SingleColumnGreedyAnalyzer
import edu.duke.cacheplanner.data.Column
import edu.duke.cacheplanner.data.Dataset
import edu.duke.cacheplanner.listener.ListenerManager
import edu.duke.cacheplanner.query.QueryUtil
import edu.duke.cacheplanner.query.SingleTableQuery
import edu.duke.cacheplanner.queue.ExternalQueue

class OnlineCachePlanner(setup: Boolean, manager: ListenerManager, 
    queues: java.util.List[ExternalQueue], data: java.util.List[Dataset], 
    time: Long) extends AbstractCachePlanner(setup, manager, queues, data) {

  val batchTime = time;
  var cachedData : scala.collection.mutable.Map[String, ArrayBuffer[Column]] = new HashMap[String, ArrayBuffer[Column]]()

  override def initPlannerThread(): Thread = {
    new Thread("OnlineCachePlanner") {
      override def run() {
        while (true) {
          println("cacheplanner workinggggggggggg")
          if (!started) {
            return
          }

          try { 
        	  Thread.sleep(batchTime)
          } catch {
            case e:InterruptedException => e.printStackTrace
          }

          if (isMultipleSetup) {
            // create a batch of queries
            var batch = scala.collection.mutable.LinkedList[SingleTableQuery]()
            for (queue <- externalQueues.toList) {
              queue.fetchABatch().toList.foreach(q => batch.add(q.asInstanceOf[SingleTableQuery]))
            }
            
            // analyze the batch to find columns to cache
            val cachedCols = cachedData.flatMap(t => t._2).toList
            val colsToCache : List[Column] = SingleColumnGreedyAnalyzer.analyzeBatch(
                batch.toList, cachedCols, 1000) //TODO: get the right memory size

            //merging candidate columns if they are in the same table
            var cacheCandidate : Map[String, ArrayBuffer[Column]] = new HashMap[String, ArrayBuffer[Column]]()
            var cacheDropCandidate : ArrayBuffer[String] = new ArrayBuffer[String]()
            for (col: Column <- colsToCache) {            
              val candidate = cacheCandidate.getOrElse(col.getDatasetName, null)
              if(candidate == null) {
                val buffer = new ArrayBuffer[Column]()
                buffer.append(col)
                cacheCandidate(col.getDatasetName) = buffer
              }
              else {
                cacheCandidate(col.getDatasetName).append(col)
              }
            }

            val next_cached = cacheCandidate.clone
            
            //check whether they are already cached in the same format
            for(datasetName <- cacheCandidate.keySet) {
              //check if the table is already cached
              val cached = cachedData.getOrElse(datasetName, null)

              if(cached != null) {
                //check the columns in dataset
                val cached_set = cachedData(datasetName).toSet
                val candidate_set = cacheCandidate(datasetName).toSet
                if(cached_set.equals(candidate_set)) {
                  //the candidate is already in cache
                  cacheCandidate.remove(datasetName)
                }
                else {
                  //need to be dropped
                  cacheDropCandidate.append(datasetName)
                }
              }
            }

            cachedData = next_cached


            // fire queries to drop the cache
            for(data <- cacheDropCandidate) {
              hiveContext.uncacheTable(data)
            }

            // fire queries to cache columns
            for(data <- cacheCandidate.keySet) {
              var drop_cache_table = QueryUtil.getDropTableSQL(data +"_cached")
              var query_create = QueryUtil.getCacheTableCreateSQL(data, cacheCandidate(data).asJava)
              var query_insert = QueryUtil.getCacheTableInsertSQL(data, cacheCandidate(data).asJava)
              hiveContext.hql(drop_cache_table)
              hiveContext.hql(query_create)
              hiveContext.hql(query_insert)
              hiveContext.cacheTable(data)
            }

            // fire other queries
            for(query <- batch.toList) {
              var queryString = ""
              if(cachedData.contains(query.asInstanceOf[SingleTableQuery].getDataset().getName())) {
                queryString = query.toHiveQL(true)
              }
              else {
                queryString = query.toHiveQL(false)
              }
              sc.setJobDescription(queryString)
              sc.setLocalProperty("spark.scheduler.pool", query.getQueueID())
              val result = hiveContext.hql(query.toHiveQL(false))
              result.collect().foreach(println)
            }
            //wait for all the threads are done

          }
          else {
            //single app mode
          }
        }
      }
    }
  }

}