package com.highstar.casqueue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class OptimizedQueue {
	public interface BarrierHolder {
		  Object getBarrier();
		}
	public void test(){
		OptimisticQueue<String> oq = new OptimisticQueue<String>(10);
		BarrierHolder bh = new BarrierHolder() {
			
			@Override
			public Object getBarrier() {
				return new Object();
			}
		};
		for(int i=0; i<100; i++){
			try {
				oq.offer(bh, "shit" + i);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		for(int i=0; i<10; i++){
			try {
				System.out.println(oq.take(new BarrierHolder() {
					
					@Override
					public Object getBarrier() {
						// TODO Auto-generated method stub
						return new Object();
					}
				}));
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public class OptimisticQueue<T> {
		private Object[] ringBuffer = null;
		private AtomicInteger offerSeq = new AtomicInteger(-1);
		private AtomicInteger takeSeq = new AtomicInteger(-1);
		private int size;
		private int mask;

		public OptimisticQueue(int sizePower) {
			this.size = 1 << sizePower;
		    this.ringBuffer = new Object[size];
		    for (int i = 0; i < size; i++) { 			
		    	ringBuffer[i] = new Entry(i + 1); 		
		    }
		    this.mask = 0x7FFFFFFF >> (31 - sizePower);
		  }

		@SuppressWarnings("unchecked")
		private Entry nextOffer() {
		return (Entry) ringBuffer[offerSeq.incrementAndGet() & mask];
		}

		@SuppressWarnings("unchecked")
		private Entry nextTake() {
			return (Entry) ringBuffer[takeSeq.incrementAndGet() & mask];
		}

		public void offer(BarrierHolder holder, T event) throws 
		InterruptedException {
			Entry entry = nextOffer();
		    Object barrier = holder.getBarrier();
		    if (!entry.enterFront(barrier)) {
		      offer(holder, event);
		      return;
		    }
		    if (entry.event != null) {
		      synchronized (barrier) {
		        while (entry.event != null) {
		          barrier.wait();
		        }
		      }
		    }
		    entry.publish(event);
		  }

		public T take(BarrierHolder consumer) throws InterruptedException {
			Entry entry = nextTake();
		    Object barrier = consumer.getBarrier();
		    if (!entry.enterBack(barrier))
		      return take(consumer);
		    if (entry.event == null) {
		      synchronized (barrier) {
		        while (entry.event == null) {
		          barrier.wait();
		        }
		      }
		    }
		    return entry.take();
		  }
		  
		private class Entry {

			private volatile T event = null ;

			private AtomicReference < Object > backDoor = new AtomicReference < Object > ( ) ;

			private AtomicReference < Object > frontDoor = new AtomicReference < Object > ( ) ;

			private int id ;
			public Entry ( int id ) {
				this . id = id ;
			}
			public void publish ( T event ) {
				
				this . event = event ;

				frontDoor. set ( null ) ;

				Object barrier = backDoor. get ( ) ;

				if ( barrier != null ) {

					synchronized ( barrier ) {

						barrier. notify ( ) ;

					}
				}
			}
			public boolean enterFront ( Object barrier ) {

				return frontDoor. compareAndSet ( null , barrier ) ;

			}
			public boolean enterBack ( Object barrier ) {

				return backDoor. compareAndSet ( null , barrier ) ;

			} 
			public T take ( ) {

				T e = event ;
				event = null ;
				backDoor. set ( null ) ;

				Object barrier = frontDoor. get ( ) ;

				if ( barrier != null ) {

					synchronized ( barrier ) {

						barrier. notify ( ) ;

					}
				}
				return e ;
			}
			public int getId() {

				return id ;
			} 
		}
	}
	public static void main(String[] args) {
		OptimizedQueue queue = new OptimizedQueue();
		queue.test();
	}
}
