package edu.duke.cacheplanner.queue;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import edu.duke.cacheplanner.listener.ListenerManager;
import edu.duke.cacheplanner.listener.QueryFetchedByCachePlanner;
import edu.duke.cacheplanner.query.AbstractQuery;

/**
 * External Queue class
 *  
 */
public class ExternalQueue {
	private int queueID;
    private String queueName;
	private int weight;
	private int minShare;
	private int batchSize; 
	private ListenerManager listenerManager;
	private Queue<AbstractQuery> queue;
	private String rankFile;

	public ExternalQueue(int id, int w, int min, int size, String name) {
		queueID = id;
		queueName = name;
		setWeight(w);
		setMinShare(min);
		batchSize = size;
		queue = new LinkedList<AbstractQuery>();
	}

	public ExternalQueue(int id, int w, int min, int size, String name, 
			ListenerManager manager) {
		this(id, w, min, size, name);
		listenerManager = manager;
	}

	/**
	 * return a batch of queries that arrived until current time
	 */
	public synchronized List<AbstractQuery> fetchABatch() { 
		List<AbstractQuery> queries = new ArrayList<AbstractQuery>();
//		int size = queue.size();	//fetch all the queries
		for(int i = 0; i < batchSize; i++) {
			if(queue.peek() != null) {
				AbstractQuery query = queue.poll();
				queries.add(query);  
				//notify an event to the listeners
				listenerManager.postEvent(new QueryFetchedByCachePlanner
						(query));
			} else {
				break;
			}
		} 
		return queries;
	}

	/**
	 * add query to the queue
	 */
	public synchronized void addQuery(AbstractQuery query) {
		queue.add(query);
	}

	public void setListenerManager(ListenerManager manager) {
		listenerManager = manager;
	}  

	public int getId() {
		return queueID;
	}

	/**
	 * @return the weight
	 */
	public int getWeight() {
		return weight;
	}

	/**
	 * @param weight the weight to set
	 */
	public void setWeight(int weight) {
		this.weight = weight;
	}

	/**
	 * @return the minShare
	 */
	public int getMinShare() {
		return minShare;
	}

  /**
   * @return queueName
   */
  public String getQueueName() {
    return queueName;
  }
	/**
	 * @param minShare the minShare to set
	 */
	public void setMinShare(int minShare) {
		this.minShare = minShare;
	}

	/**
	 * @return the zipfRanks
	 */
	public String getRankFile() {
		return rankFile;
	}

	/**
	 * @param zipfRanks the zipfRanks to set
	 */
	public void setRankFile(String rankFile) {
		this.rankFile = rankFile;
	}
}
