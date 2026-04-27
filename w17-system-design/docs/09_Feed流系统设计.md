# Feed 流系统设计

> 经典系统设计题：设计类似 Twitter/朋友圈的 Feed 流系统

## 一、需求澄清

**核心功能**：
- 发布动态：用户发布文字、图片、视频
- 查看 Feed：查看关注用户的动态（时间倒序）
- 点赞评论：对动态点赞、评论
- 关注用户：关注/取消关注

**非功能需求**：
- 日活用户：1 亿
- 每个用户关注 200 人
- 每个用户每天发 1 条动态
- 每个用户每天刷 10 次 Feed

## 二、容量估算

**QPS 估算**：
```
发布动态 QPS = 100,000,000 × 1 / 86400 = 1157 QPS
查看 Feed QPS = 100,000,000 × 10 / 86400 = 11574 QPS
读写比 = 10:1
```

**存储估算**：
```
单条动态：1 KB（文字 + 元数据）
每天新增：100,000,000 条
保存 1 年：100,000,000 × 365 = 36,500,000,000（365 亿）
存储容量 = 1 KB × 36,500,000,000 × 2 × 1.3 = 95 TB
```

## 三、高层架构

### 3.1 推模式 vs 拉模式

**推模式（写扩散）**：
```
用户 A 发布动态
  ↓
写入 A 的粉丝的 Feed 列表（200 个粉丝）
  ↓
粉丝查看 Feed（直接读取）

优点：读快（直接读取）
缺点：写慢（需要写 200 次）、存储大
适用：粉丝少的用户
```

**拉模式（读扩散）**：
```
用户 A 发布动态
  ↓
写入 A 的动态列表
  ↓
粉丝查看 Feed
  ↓
查询所有关注用户的动态（200 个用户）
  ↓
合并排序

优点：写快（只写 1 次）、存储小
缺点：读慢（需要查询 200 次）
适用：粉丝多的用户（大 V）
```

**推拉结合（推荐）**：
```
普通用户：推模式（粉丝 < 1000）
大 V：拉模式（粉丝 > 1000）
```

### 3.2 架构图

```
用户
  ↓
CDN（图片、视频）
  ↓
Nginx
  ↓
┌──────────────┬──────────────┬──────────────┐
│ Feed Service │ Post Service │ User Service │
└──────┬───────┴──────┬───────┴──────┬───────┘
       │              │              │
   ┌───▼───┐      ┌───▼───┐      ┌───▼───┐
   │ Redis │      │ MySQL │      │ Kafka │
   │ ZSet  │      │ 分库  │      │       │
   │(时间线)│      │ 分表  │      │       │
   └───────┘      └───────┘      └───────┘
```

## 四、深入设计

### 4.1 数据模型

**动态表（post）**：
```sql
CREATE TABLE post (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  content TEXT,
  images VARCHAR(1000),
  like_count INT DEFAULT 0,
  comment_count INT DEFAULT 0,
  create_time DATETIME,
  INDEX idx_user_time (user_id, create_time)
);
```

**关注表（follow）**：
```sql
CREATE TABLE follow (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  follow_user_id BIGINT NOT NULL,
  create_time DATETIME,
  UNIQUE KEY uk_user_follow (user_id, follow_user_id),
  INDEX idx_follow_user (follow_user_id)
);
```

**Feed 时间线（Redis ZSet）**：
```
# 用户的 Feed 列表（推模式）
ZADD feed:10001 1714204800 post:1  # score 是时间戳
ZADD feed:10001 1714204900 post:2
ZADD feed:10001 1714205000 post:3

# 查询最新 10 条
ZREVRANGE feed:10001 0 9
```

### 4.2 核心流程

**发布动态（推模式）**：
```java
@PostMapping("/post")
public Result<Long> publish(@RequestBody PostRequest request) {
    // 1. 创建动态
    Post post = new Post();
    post.setUserId(request.getUserId());
    post.setContent(request.getContent());
    postService.save(post);
    
    // 2. 查询粉丝列表
    List<Long> fans = followService.getFans(request.getUserId());
    
    // 3. 推送到粉丝的 Feed 列表（异步）
    kafkaTemplate.send("post-publish", new PublishEvent(post.getId(), fans));
    
    return Result.success(post.getId());
}

@KafkaListener(topics = "post-publish")
public void onPublish(PublishEvent event) {
    // 写入粉丝的 Feed 列表
    for (Long fanId : event.getFans()) {
        redisTemplate.opsForZSet().add(
            "feed:" + fanId, 
            "post:" + event.getPostId(), 
            System.currentTimeMillis()
        );
    }
}
```

**查看 Feed（推模式）**：
```java
@GetMapping("/feed")
public Result<List<Post>> getFeed(@RequestParam Long userId, 
                                   @RequestParam Integer page) {
    // 1. 从 Redis 查询 Feed 列表
    Set<String> postIds = redisTemplate.opsForZSet().reverseRange(
        "feed:" + userId, 
        (page - 1) * 10, 
        page * 10 - 1
    );
    
    // 2. 批量查询动态详情
    List<Post> posts = postService.batchGet(postIds);
    
    return Result.success(posts);
}
```

**查看 Feed（拉模式）**：
```java
@GetMapping("/feed")
public Result<List<Post>> getFeed(@RequestParam Long userId) {
    // 1. 查询关注列表
    List<Long> followUsers = followService.getFollowList(userId);
    
    // 2. 查询每个用户的最新动态（并行）
    List<CompletableFuture<List<Post>>> futures = followUsers.stream()
        .map(followUserId -> CompletableFuture.supplyAsync(() -> 
            postService.getLatestPosts(followUserId, 10)
        ))
        .collect(Collectors.toList());
    
    // 3. 合并排序
    List<Post> allPosts = futures.stream()
        .map(CompletableFuture::join)
        .flatMap(List::stream)
        .sorted(Comparator.comparing(Post::getCreateTime).reversed())
        .limit(10)
        .collect(Collectors.toList());
    
    return Result.success(allPosts);
}
```

### 4.3 高可用设计

**缓存策略**：
```
L1: Redis（Feed 时间线）
L2: MySQL（动态详情）

缓存过期：7 天
缓存预热：用户登录时预加载
```

**限流降级**：
```
限流：用户级别 10 req/s
降级：关闭推荐功能，只显示关注用户动态
```

### 4.4 扩展性设计

**分库分表**：
```
按 user_id Hash 分片
8 个库 × 16 张表 = 128 张表
```

**Redis Cluster**：
```
Feed 时间线分片存储
按 user_id Hash 分片
```

## 五、优化方案

**热点数据缓存**：
```java
// 大 V 的动态缓存到 Redis
if (user.getFansCount() > 10000) {
    redisTemplate.opsForValue().set("post:" + postId, post, 1, TimeUnit.HOURS);
}
```

**图片视频 CDN**：
```
图片上传到 OSS
CDN 加速访问
```

**分页优化**：
```java
// 游标分页（避免深分页）
@GetMapping("/feed")
public Result<List<Post>> getFeed(@RequestParam Long userId, 
                                   @RequestParam Long cursor) {
    Set<String> postIds = redisTemplate.opsForZSet().reverseRangeByScore(
        "feed:" + userId, 
        0, 
        cursor, 
        0, 
        10
    );
    // ...
}
```

## 六、面试要点

**核心设计**：
- 推拉结合：普通用户推模式，大 V 拉模式
- Redis ZSet：存储 Feed 时间线
- 异步推送：Kafka 异步写入粉丝 Feed
- 分库分表：按 user_id Hash 分片

**常见追问**：
- "推模式 vs 拉模式？" → "推拉结合，根据粉丝数选择"
- "如何处理大 V？" → "拉模式，实时查询"
- "如何优化性能？" → "Redis 缓存 + CDN + 分库分表"

**关键数据**：
- QPS：11574（读多写少 10:1）
- 存储：95 TB（365 亿动态）
- 服务器：10 台应用 + 8 个 MySQL + Redis Cluster

**下一步**：D101 分布式缓存系统设计
