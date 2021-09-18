# LinkedHashMap理解

LinkedHashMap最常用于LRU算法的缓存实现（使用accessOrder模式），另一个模式是按插入顺序。此外它还保证了顺序。

## 与HashMap的关系

继承于HashMap，并重写了newNode方法，它的Node类型也继承了HashMap的Node，主要是为了支持双向链表增加了before和after节点。整体存储方式还是依赖了HashMap，即put和get依赖了HashMap的实现，但对于accessOrder模式时，对get会调整链表的顺序。

## LRU过程实现

当accessOrder=true的时候，LinkedHashMap通过重写onNodeAccess，会把访问的元素从链表取出，并重新放在链表尾端（head, tail都存在成员变量）。列表头是最少被访问的，因此是最少被使用的（但如果，这个对象以其他方式在不断被使用，那么另当别论）。但整个过程LinkedHashMap都不会自动进行删除操作。如果使用者需要增加删除逻辑，可以复写removeEldestEntry(Node node)，这是在put时，会触发这个方法，可以在这里返回布尔值，方便决定是否直接删除最老的节点（头节点）。