Design notes
- ConcurrentObjectPool uses java locks and conditions to make usage thread safe. There are 3 collections (not thread safe itself)
to store available, acquired and candidates to remove on release. Locks guarantee that transitions of elements between them are atomic
from multi thread point of view
- There is no restriction for immutability of the objects but acquired and released resources
 (as well as added and removed) must be strictly the same (no equal equivalents)
- Pool does not allow null elements
- Pool does not extend Closable because according to requirements close methods must wait until all resources released 
and using pool in try with resources block may show unexpected behavior
- After close and closeNow methods invocation all resources are removed (no requirements for that)
- After close and closeNow methods pool can be opened (no requirements for that)
- Added some simple tests for business logic and some simple concurrent tests to check locks

Ways to improve

Limited time does not allow to improve significantly. But there are some ways to do it in future:
- Use read and write locks (isOpenedLock) 
- Define strategy for creation of internal collections (now identity is used to ensure equality)
- On acquire method new iterator is created - for heavily usage with large objects amount and
 high acquire/release load it is not good
- It should be tested by high load tests (they are need to be created)
- Only single lock (acquireLock) ensures thread safety, there might be better way with 
multiple locks