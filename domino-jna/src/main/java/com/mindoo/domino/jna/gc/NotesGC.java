package com.mindoo.domino.jna.gc;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

import com.mindoo.domino.jna.errors.NotesError;
import com.mindoo.domino.jna.internal.NotesJNAContext;

/**
 * Utility class to simplify memory management with Notes handles. The class tracks
 * handle creation and disposal. By using {@link #runWithAutoGC(Callable)}, the
 * collected handles are automatically disposed when code execution is done.
 * 
 * @author Karsten Lehmann
 */
public class NotesGC {
	private static ThreadLocal<Boolean> m_activeAutoGC = new ThreadLocal<Boolean>();
	private static ThreadLocal<Map<String,Object>> m_activeAutoGCCustomValues = new ThreadLocal<Map<String,Object>>();
	
	//maps with open handles; using LinkedHashMap to keep insertion order for the keys
	private static ThreadLocal<LinkedHashMap<Integer,IRecyclableNotesObject>> m_b32OpenHandlesDominoObjects = new ThreadLocal<LinkedHashMap<Integer,IRecyclableNotesObject>>();
	private static ThreadLocal<LinkedHashMap<Integer,IAllocatedMemory>> m_b32OpenHandlesMemory = new ThreadLocal<LinkedHashMap<Integer,IAllocatedMemory>>();
	private static ThreadLocal<LinkedHashMap<Long, IRecyclableNotesObject>> m_b64OpenHandlesDominoObjects = new ThreadLocal<LinkedHashMap<Long,IRecyclableNotesObject>>();
	private static ThreadLocal<LinkedHashMap<Long, IAllocatedMemory>> m_b64OpenHandlesMemory = new ThreadLocal<LinkedHashMap<Long,IAllocatedMemory>>();
	
	private static ThreadLocal<Boolean> m_writeDebugMessages = new ThreadLocal<Boolean>() {
		protected Boolean initialValue() {
			return Boolean.FALSE;
		};
	};
	
	/**
	 * Method to enable GC debug logging for the current {@link #runWithAutoGC(Callable)} call
	 * 
	 * @param enabled
	 */
	public static void setDebugLoggingEnabled(boolean enabled) {
		m_writeDebugMessages.set(Boolean.valueOf(enabled));
	}
	
	/**
	 * Method to get the current count of open Domino object handles
	 * 
	 * @return handle count
	 */
	public static int getNumberOfOpenObjectHandles() {
		if (!Boolean.TRUE.equals(m_activeAutoGC.get()))
			throw new IllegalStateException("Auto GC is not active");
		
		if (NotesJNAContext.is64Bit()) {
			return m_b64OpenHandlesDominoObjects.get().size();
		}
		else {
			return m_b32OpenHandlesDominoObjects.get().size();
		}
	}

	/**
	 * Method to get the current count of open Domino memory handles
	 * 
	 * @return handle count
	 */
	public static int getNumberOfOpenMemoryHandles() {
		if (!Boolean.TRUE.equals(m_activeAutoGC.get()))
			throw new IllegalStateException("Auto GC is not active");
		
		if (NotesJNAContext.is64Bit()) {
			return m_b64OpenHandlesMemory.get().size();
		}
		else {
			return m_b32OpenHandlesMemory.get().size();
		}
	}

	/**
	 * Internal method to register a created Notes object that needs to be recycled
	 * 
	 * @param obj Notes object
	 */
	public static void __objectCreated(IRecyclableNotesObject obj) {
		if (!Boolean.TRUE.equals(m_activeAutoGC.get()))
			throw new IllegalStateException("Auto GC is not active");
		
		if (obj.isRecycled())
			throw new NotesError(0, "Object is already recycled");
		
		
		if (NotesJNAContext.is64Bit()) {
			IRecyclableNotesObject oldObj = m_b64OpenHandlesDominoObjects.get().put(obj.getHandle64(), obj);
			if (oldObj!=null && oldObj!=obj) {
				throw new IllegalStateException("Duplicate handle detected. Object to store: "+obj+", object found in open handle list: "+oldObj);
			}
		}
		else {
			IRecyclableNotesObject oldObj = m_b32OpenHandlesDominoObjects.get().put(obj.getHandle32(), obj);
			if (oldObj!=null && oldObj!=obj) {
				throw new IllegalStateException("Duplicate handle detected. Object to store: "+obj+", object found in open handle list: "+oldObj);
			}
		}
		
		if (Boolean.TRUE.equals(m_writeDebugMessages.get())) {
			System.out.println("AutoGC - Added object: "+obj);
		}
	}

	/**
	 * Internal method to register a created Notes object that needs to be recycled
	 * 
	 * @param mem Notes object
	 */
	public static void __memoryAllocated(IAllocatedMemory mem) {
		if (!Boolean.TRUE.equals(m_activeAutoGC.get()))
			throw new IllegalStateException("Auto GC is not active");
		
		if (mem.isFreed())
			throw new NotesError(0, "Memory is already freed");
		
		
		if (NotesJNAContext.is64Bit()) {
			IAllocatedMemory oldObj = m_b64OpenHandlesMemory.get().put(mem.getHandle64(), mem);
			if (oldObj!=null && oldObj!=mem) {
				throw new IllegalStateException("Duplicate handle detected. Memory to store: "+mem+", object found in open handle list: "+oldObj);
			}
		}
		else {
			IAllocatedMemory oldObj = m_b32OpenHandlesMemory.get().put(mem.getHandle32(), mem);
			if (oldObj!=null && oldObj!=mem) {
				throw new IllegalStateException("Duplicate handle detected. Memory to store: "+mem+", object found in open handle list: "+oldObj);
			}
		}
		
		if (Boolean.TRUE.equals(m_writeDebugMessages.get())) {
			System.out.println("AutoGC - Added memory: "+mem);
		}
	}

	/**
	 * Internal method to check whether a 64 bit handle exists
	 * 
	 * @param objClazz class of Notes object
	 * @param handle handle
	 * @throws NotesError if handle does not exist
	 */
	public static void __b64_checkValidObjectHandle(Class<? extends IRecyclableNotesObject> objClazz, long handle) {
		if (!Boolean.TRUE.equals(m_activeAutoGC.get()))
			throw new IllegalStateException("Auto GC is not active");
		
		IRecyclableNotesObject obj = m_b64OpenHandlesDominoObjects.get().get(handle);
		if (obj==null) {
			throw new NotesError(0, "The provided C handle "+handle+" of object with class "+objClazz.getName()+" does not seem to exist (anymore).");
		}
	}

	/**
	 * Internal method to check whether a 64 bit handle exists
	 * 
	 * @param memClazz class of Notes object
	 * @param handle handle
	 * @throws NotesError if handle does not exist
	 */
	public static void __b64_checkValidMemHandle(Class<? extends IAllocatedMemory> objClazz, long handle) {
		if (!Boolean.TRUE.equals(m_activeAutoGC.get()))
			throw new IllegalStateException("Auto GC is not active");
		
		IAllocatedMemory obj = m_b64OpenHandlesMemory.get().get(handle);
		if (obj==null) {
			throw new NotesError(0, "The provided C handle "+handle+" of memory with class "+objClazz.getName()+" does not seem to exist (anymore).");
		}
	}

	/**
	 * Internal method to check whether a 32 bit handle exists
	 * 
	 * @param objClazz class of Notes object
	 * @param handle handle
	 * @throws NotesError if handle does not exist
	 */
	public static void __b32_checkValidObjectHandle(Class<? extends IRecyclableNotesObject> objClazz, int handle) {
		if (!Boolean.TRUE.equals(m_activeAutoGC.get()))
			throw new IllegalStateException("Auto GC is not active");
		
		IRecyclableNotesObject obj = m_b32OpenHandlesDominoObjects.get().get(handle);
		if (obj==null) {
			throw new NotesError(0, "The provided C handle "+handle+" of object with class "+objClazz.getName()+" does not seem to exist (anymore).");
		}
	}

	/**
	 * Internal method to check whether a 32 bit handle exists
	 * 
	 * @param objClazz class of Notes object
	 * @param handle handle
	 * @throws NotesError if handle does not exist
	 */
	public static void __b32_checkValidMemHandle(Class<? extends IAllocatedMemory> objClazz, int handle) {
		if (!Boolean.TRUE.equals(m_activeAutoGC.get()))
			throw new IllegalStateException("Auto GC is not active");
		
		IAllocatedMemory obj = m_b32OpenHandlesMemory.get().get(handle);
		if (obj==null) {
			throw new NotesError(0, "The provided C handle "+handle+" of memory with class "+objClazz.getName()+" does not seem to exist (anymore).");
		}
	}

	/**
	 * Internal method to unregister a created Notes object that was recycled
	 * 
	 * @param obj Notes object
	 */
	public static void __objectBeeingBeRecycled(IRecyclableNotesObject obj) {
		if (!Boolean.TRUE.equals(m_activeAutoGC.get()))
			throw new IllegalStateException("Auto GC is not active");
		
		if (obj.isRecycled())
			throw new NotesError(0, "Object is already recycled");

		if (Boolean.TRUE.equals(m_writeDebugMessages.get())) {
			System.out.println("AutoGC - Removing object: "+obj.getClass()+" with handle="+(NotesJNAContext.is64Bit() ? obj.getHandle64() : obj.getHandle32()));
		}
		
		if (NotesJNAContext.is64Bit()) {
			m_b64OpenHandlesDominoObjects.get().remove(obj.getHandle64());
		}
		else {
			m_b32OpenHandlesDominoObjects.get().remove(obj.getHandle32());
		}
	}

	/**
	 * Internal method to unregister a created Notes object that was recycled
	 * 
	 * @param mem Notes object
	 */
	public static void __memoryBeeingFreed(IAllocatedMemory mem) {
		if (!Boolean.TRUE.equals(m_activeAutoGC.get()))
			throw new IllegalStateException("Auto GC is not active");
		
		if (mem.isFreed())
			throw new NotesError(0, "Memory has already been freed");

		if (Boolean.TRUE.equals(m_writeDebugMessages.get())) {
			System.out.println("AutoGC - Removing memory: "+mem.getClass()+" with handle="+(NotesJNAContext.is64Bit() ? mem.getHandle64() : mem.getHandle32()));
		}
		
		if (NotesJNAContext.is64Bit()) {
			m_b64OpenHandlesMemory.get().remove(mem.getHandle64());
		}
		else {
			m_b32OpenHandlesMemory.get().remove(mem.getHandle32());
		}
	}

	public static Object setCustomValue(String key, Object value) {
		Map<String,Object> map = m_activeAutoGCCustomValues.get();
		if (map==null) {
			throw new IllegalStateException("No auto gc block is active");
		}
		return map.put(key, value);
	}
	
	public static Object getCustomValue(String key) {
		Map<String,Object> map = m_activeAutoGCCustomValues.get();
		if (map==null) {
			throw new IllegalStateException("No auto gc block is active");
		}
		return map.get(key);
	}
	
	public boolean hasCustomValue(String key) {
		Map<String,Object> map = m_activeAutoGCCustomValues.get();
		if (map==null) {
			throw new IllegalStateException("No auto gc block is active");
		}
		return map.containsKey(key);
	}
	
	/**
	 * Runs a piece of code and automatically disposes any allocated Notes objects at the end.
	 * The method supported nested calls.
	 * 
	 * @param callable code to execute
	 * @return computation result
	 * @throws Exception in case of errors
	 * 
	 * @param <T> return value type of code to be run
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static <T> T runWithAutoGC(Callable<T> callable) throws Exception {
		if (Boolean.TRUE.equals(m_activeAutoGC.get())) {
			//nested call
			return callable.call();
		}
		else {
			m_activeAutoGC.set(Boolean.TRUE);
			m_activeAutoGCCustomValues.set(new HashMap<String, Object>());
			
			LinkedHashMap<Integer,IRecyclableNotesObject> b32HandlesDominoObjects = null;
			LinkedHashMap<Long,IRecyclableNotesObject> b64HandlesDominoObjects = null;
			
			LinkedHashMap<Integer,IAllocatedMemory> b32HandlesMemory = null;
			LinkedHashMap<Long,IAllocatedMemory> b64HandlesMemory = null;
			
			try {
				if (NotesJNAContext.is64Bit()) {
					b64HandlesDominoObjects = new LinkedHashMap<Long,IRecyclableNotesObject>();
					m_b64OpenHandlesDominoObjects.set(b64HandlesDominoObjects);
					
					b64HandlesMemory = new LinkedHashMap<Long,IAllocatedMemory>();
					m_b64OpenHandlesMemory.set(b64HandlesMemory);
				}
				else {
					b32HandlesDominoObjects = new LinkedHashMap<Integer,IRecyclableNotesObject>();
					m_b32OpenHandlesDominoObjects.set(b32HandlesDominoObjects);
					
					b32HandlesMemory = new LinkedHashMap<Integer,IAllocatedMemory>();
					m_b32OpenHandlesMemory.set(b32HandlesMemory);
				}
				
				return callable.call();
			}
			finally {
				boolean writeDebugMsg = Boolean.TRUE.equals(m_writeDebugMessages.get());
				
				if (NotesJNAContext.is64Bit()) {
					{
						//recycle created Domino objects
						Entry[] mapEntries = b64HandlesDominoObjects.entrySet().toArray(new Entry[b64HandlesDominoObjects.size()]);
						if (mapEntries.length>0) {
							if (writeDebugMsg) {
								System.out.println("AutoGC - Auto-recycling "+mapEntries.length+" Domino objects:");
							}
							
							for (int i=mapEntries.length-1; i>=0; i--) {
								Entry<Long,IRecyclableNotesObject> currEntry = mapEntries[i];
								IRecyclableNotesObject obj = currEntry.getValue();
								try {
									if (!obj.isRecycled()) {
										if (writeDebugMsg) {
											System.out.println("AutoGC - Auto-recycling "+obj);
										}
										obj.recycle();
									}
								}
								catch (Throwable e) {
									e.printStackTrace();
								}
								b64HandlesDominoObjects.remove(currEntry.getKey());
							}
							
							if (writeDebugMsg) {
								System.out.println("AutoGC - Done auto-recycling "+mapEntries.length+" Domino objects");
							}
							
							b64HandlesDominoObjects.clear();
							m_b64OpenHandlesDominoObjects.set(null);
						}
					}
					{
						//dispose allocated memory
						Entry[] mapEntries = b64HandlesMemory.entrySet().toArray(new Entry[b64HandlesMemory.size()]);
						if (mapEntries.length>0) {
							if (writeDebugMsg) {
								System.out.println("AutoGC - Freeing "+mapEntries.length+" memory handles");
							}

							for (int i=mapEntries.length-1; i>=0; i--) {
								Entry<Long,IAllocatedMemory> currEntry = mapEntries[i];
								IAllocatedMemory obj = currEntry.getValue();
								try {
									if (!obj.isFreed()) {
										if (writeDebugMsg) {
											System.out.println("AutoGC - Freeing "+obj);
										}
										obj.free();
									}
								}
								catch (Throwable e) {
									e.printStackTrace();
								}
								b64HandlesMemory.remove(currEntry.getKey());
							}
							
							if (writeDebugMsg) {
								System.out.println("AutoGC - Done freeing "+mapEntries.length+" memory handles");
							}
							
							b64HandlesMemory.clear();
							m_b64OpenHandlesMemory.set(null);
						}
					}
				}
				else {
					{
						//recycle created Domino objects
						Entry[] mapEntries = b32HandlesDominoObjects.entrySet().toArray(new Entry[b32HandlesDominoObjects.size()]);
						if (mapEntries.length>0) {
							if (writeDebugMsg) {
								System.out.println("AutoGC - Recycling "+mapEntries.length+" Domino objects:");
							}

							for (int i=mapEntries.length-1; i>=0; i--) {
								Entry<Integer,IRecyclableNotesObject> currEntry = mapEntries[i];
								IRecyclableNotesObject obj = currEntry.getValue();
								try {
									if (!obj.isRecycled()) {
										if (writeDebugMsg) {
											System.out.println("AutoGC - Recycling "+obj);
										}
										obj.recycle();
									}
								}
								catch (Throwable e) {
									e.printStackTrace();
								}
								b32HandlesDominoObjects.remove(currEntry.getKey());
							}
							if (writeDebugMsg) {
								System.out.println("AutoGC - Done recycling "+mapEntries.length+" memory handles");
							}
							
							b32HandlesDominoObjects.clear();
							m_b32OpenHandlesDominoObjects.set(null);
						}
					}
					{
						//dispose allocated memory
						Entry[] mapEntries = b32HandlesMemory.entrySet().toArray(new Entry[b32HandlesMemory.size()]);
						if (mapEntries.length>0) {
							if (writeDebugMsg) {
								System.out.println("AutoGC - Freeing "+mapEntries.length+" memory handles");
							}

							for (int i=mapEntries.length-1; i>=0; i--) {
								Entry<Integer,IAllocatedMemory> currEntry = mapEntries[i];
								IAllocatedMemory obj = currEntry.getValue();
								try {
									if (!obj.isFreed()) {
										if (writeDebugMsg) {
											System.out.println("AutoGC - Freeing "+obj);
										}
										obj.free();
									}
								}
								catch (Throwable e) {
									e.printStackTrace();
								}
								b32HandlesMemory.remove(currEntry.getKey());
							}
							if (writeDebugMsg) {
								System.out.println("AutoGC - Done freeing "+mapEntries.length+" memory handles");
							}
							
							b32HandlesMemory.clear();
							m_b32OpenHandlesMemory.set(null);
						}
					}
				}
				m_activeAutoGCCustomValues.set(null);
				m_activeAutoGC.set(null);
				m_writeDebugMessages.set(Boolean.FALSE);
			}
		}
	}
}
