# -*- coding: utf-8 -*-
"""
把桌面上的 收入.md / 总资产.md 转成应用可导入的 JSON。

严格对齐 app/src/main/java/io/github/zyraxi21/accountbook/domain/BookSerialization.kt 的解码规则：
  - 顶层必须是对象，含 version(整数<=1)、channels、snapshots、incomes 三个数组
  - 金额一律用字符串，避免二进制浮点误差（JsonReader 遇到小数直接报错）
  - month 必须等于 registeredAt 在 Asia/Shanghai 下的年月
  - balances 里的 channelId 必须在 channels 中出现
  - liability 与各余额不能为负；收入金额必须大于 0
"""
import json
import re
from decimal import Decimal
from pathlib import Path

DESKTOP = Path(r"C:\Users\zyraxi\Desktop")
OUT = DESKTOP / "账本导入.json"

# ---------------------------------------------------------------- 工具


def money(text) -> str:
    """金额统一成两位小数的字符串；顺带校验是否超出两位小数。"""
    value = Decimal(str(text).strip())
    quantized = value.quantize(Decimal("0.01"))
    if quantized != value:
        raise ValueError(f"金额 {text} 超过两位小数")
    if value < 0:
        raise ValueError(f"金额 {text} 为负，资产与负债不允许")
    return f"{quantized:.2f}"


def table_rows(path: Path):
    """读取 Markdown 表格，返回去掉分隔行的单元格列表。"""
    rows = []
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line.startswith("|"):
            continue
        cells = [c.strip() for c in line.strip("|").split("|")]
        if all(re.fullmatch(r":?-{2,}:?", c) for c in cells):
            continue  # 分隔行
        rows.append(cells)
    return rows


def is_blank(value: str) -> bool:
    return value == "" or value.lower() == "nan"


# ---------------------------------------------------------------- 收入

income_rows = table_rows(DESKTOP / "收入.md")
header = income_rows[0]
col = {name: i for i, name in enumerate(header)}
print("收入表头:", header)

incomes = []
running = Decimal("0")
for row in income_rows[1:]:
    title = row[col["项目"]]
    amount = Decimal(row[col["金额"]])
    date = row[col["日期"]]
    running += amount
    # 顺带核对 Markdown 里的累计列，确认没有漏行或错行
    listed = row[col["累计"]]
    if Decimal(listed) != running:
        raise ValueError(f"累计核对失败：{title} 期望 {running} 实际 {listed}")
    incomes.append(
        {
            "id": f"inc-{len(incomes) + 1:03d}",
            "title": title,
            "amount": money(amount),
            # 日期无具体时刻，取 UTC 午夜；东八区即当日 08:00，日期与月份都不偏移
            "receivedAt": f"{date}T00:00:00Z",
            "source": "MANUAL",
        }
    )

print(f"收入 {len(incomes)} 条，合计 {running} 元")

# ---------------------------------------------------------------- 资产

asset_rows = table_rows(DESKTOP / "总资产.md")
print("资产表头:", asset_rows[1])

# 第一张表头行是 "账本 | Unnamed: 1..."，第二行才是真正的列名
asset_header = {name: i for i, name in enumerate(asset_rows[1])}

CHANNEL_IDS = {
    "银行": "ch-bank",
    "支付宝": "ch-alipay",
    "微信": "ch-wechat",
}
channels = [
    {"id": cid, "name": name, "active": True, "position": pos}
    for pos, (name, cid) in enumerate(CHANNEL_IDS.items())
]

snapshots = []
current = None
listed_total = {}  # 供核对 Markdown 的总额列
for row in asset_rows[2:]:
    date = row[asset_header["日期"]]
    channel = row[asset_header["渠道"]]
    amount = row[asset_header["分项金额"]]
    if not is_blank(date):
        # 新月份的资产表；总额与差额都是应用自己算出来的，这里只用来核对
        listed_total[date] = row[asset_header["总额"]]
        current = {
            "month": date[:7],
            "registeredAt": f"{date}T00:00:00Z",
            # 原表没有负债列，按 0 处理
            "liability": money(0),
            "balances": [],
        }
        snapshots.append(current)
    if is_blank(amount):
        raise ValueError(f"{date} {channel} 缺少分项金额")
    current["balances"].append(
        {
            "channelId": CHANNEL_IDS[channel],
            "channelName": channel,
            "amount": money(amount),
        }
    )

for snapshot in snapshots:
    computed = sum(Decimal(b["amount"]) for b in snapshot["balances"])
    date = snapshot["registeredAt"][:10]
    listed = Decimal(listed_total[date])
    flag = "OK" if computed == listed else "不一致"
    print(f"{snapshot['month']}  合计 {computed:>10}  表内总额 {listed:>10}  {flag}")
    if computed != listed:
        raise ValueError(f"{snapshot['month']} 总额核对失败")

# 差额 = 本月总额 − 上月总额，同样由应用计算，这里核对原表
for previous, snapshot in zip(snapshots, snapshots[1:]):
    diff = sum(Decimal(b["amount"]) for b in snapshot["balances"]) - sum(
        Decimal(b["amount"]) for b in previous["balances"]
    )
    print(f"{snapshot['month']}  差额 {diff:>10}")

# ---------------------------------------------------------------- 输出

document = {
    "version": 1,
    "exportedAt": "2026-10-07T06:00:00Z",
    "channels": channels,
    "snapshots": snapshots,
    "incomes": incomes,
}

OUT.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"\n已写出 {OUT}  记录数 {len(channels) + len(snapshots) + len(incomes)}")
