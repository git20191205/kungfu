#!/bin/bash
# ============================================
# 秒杀系统快速压测脚本
# 用法: bash benchmark.sh [并发数] [总请求数]
# 示例: bash benchmark.sh 100 1000
# 前置: 4 个微服务已启动
# ============================================

GATEWAY=http://localhost:8090
CONCURRENCY=${1:-100}
TOTAL=${2:-1000}
ACTIVITY_ID=1
TMPDIR=/tmp/seckill_bench

echo "========================================"
echo "  秒杀系统压测 — Shell 版"
echo "========================================"
echo ""
echo "  并发数: $CONCURRENCY"
echo "  总请求: $TOTAL"
echo ""

# 1. 预热
echo "=== 预热 ==="
curl -s -X POST $GATEWAY/api/seckill/activity/$ACTIVITY_ID/warmup > /dev/null
echo "  预热完成"

# 查询初始库存
STOCK_BEFORE=$(curl -s $GATEWAY/api/seckill/activity/$ACTIVITY_ID | \
  python -c "import sys,json; print(json.load(sys.stdin)['data']['remainStock'])" 2>/dev/null)
echo "  初始库存: $STOCK_BEFORE"
echo ""

# 2. 清理临时目录
rm -rf $TMPDIR && mkdir -p $TMPDIR

# 3. 发压
echo "=== 开始压测 ==="
START=$(date +%s%N)
SUCCESS=0
FAIL=0

# 分批发送，每批 CONCURRENCY 个并发
BATCH_COUNT=$(( (TOTAL + CONCURRENCY - 1) / CONCURRENCY ))
UID_BASE=500000

for batch in $(seq 1 $BATCH_COUNT); do
  BATCH_SIZE=$CONCURRENCY
  REMAINING=$(( TOTAL - (batch - 1) * CONCURRENCY ))
  if [ $REMAINING -lt $CONCURRENCY ]; then
    BATCH_SIZE=$REMAINING
  fi

  for i in $(seq 1 $BATCH_SIZE); do
    uid=$(( UID_BASE + (batch - 1) * CONCURRENCY + i ))
    curl -s -o $TMPDIR/r_${uid}.txt -w "%{http_code}" \
      -X POST $GATEWAY/api/seckill/$ACTIVITY_ID \
      -H "X-User-Id: $uid" > $TMPDIR/code_${uid}.txt &
  done
  wait
done

END=$(date +%s%N)
ELAPSED_MS=$(( (END - START) / 1000000 ))

# 4. 统计结果
for f in $TMPDIR/code_*.txt; do
  code=$(cat "$f" 2>/dev/null)
  if [ "$code" = "200" ]; then
    SUCCESS=$((SUCCESS + 1))
  else
    FAIL=$((FAIL + 1))
  fi
done

QPS=$(echo "scale=0; $TOTAL * 1000 / $ELAPSED_MS" | bc 2>/dev/null || echo "N/A")

echo ""
echo "=== 压测结果 ==="
echo "  总请求:   $TOTAL"
echo "  成功:     $SUCCESS"
echo "  失败:     $FAIL"
echo "  耗时:     ${ELAPSED_MS}ms"
echo "  QPS:      $QPS"
echo ""

# 5. 库存一致性验证
STOCK_AFTER=$(curl -s $GATEWAY/api/seckill/activity/$ACTIVITY_ID | \
  python -c "import sys,json; print(json.load(sys.stdin)['data']['remainStock'])" 2>/dev/null)
EXPECTED=$(( STOCK_BEFORE - SUCCESS ))

echo "=== 库存一致性 ==="
echo "  压测前库存: $STOCK_BEFORE"
echo "  成功扣减:   $SUCCESS"
echo "  预期库存:   $EXPECTED"
echo "  实际库存:   $STOCK_AFTER"
if [ "$STOCK_AFTER" = "$EXPECTED" ]; then
  echo "  ✓ 库存一致"
else
  echo "  ✗ 库存不一致!"
fi
echo ""

# 6. 清理
rm -rf $TMPDIR
echo "=== 压测完成 ==="
