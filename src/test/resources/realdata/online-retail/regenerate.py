#!/usr/bin/env python3
"""Regenerate the vendored Online Retail snapshot and print the pinned reference
values used by the Java benchmark.

This script is the INDEPENDENT reference: it reproduces the exact CSV snapshot
from the public source and recomputes, in pure Python, every total the Java
engine is validated against. Run it with no arguments:

    python3 regenerate.py

It writes `online_retail_head.csv` next to itself and prints the pinned values.
Run it with `--check` to assert the committed CSV is byte-identical to a fresh
pull (provenance check).

Source : UCI "Online Retail" (Daqing Chen, 2015), DOI 10.24432/C5BW33, CC BY 4.0.
Access : HuggingFace datasets-server rows API, mirror `aarav912/online-retail`.
"""
import csv
import decimal
import json
import os
import sys
import urllib.request
from collections import OrderedDict, defaultdict

DATASET = "aarav912/online-retail"
BASE = ("https://datasets-server.huggingface.co/rows"
        "?dataset=aarav912%2Fonline-retail&config=default&split=train")
ROWS = 2000           # pull the first 2000 rows, then cut at a complete invoice
HERE = os.path.dirname(os.path.abspath(__file__))
CSV_PATH = os.path.join(HERE, "online_retail_head.csv")


def fetch_rows(n):
    rows = []
    for offset in range(0, n, 100):
        url = f"{BASE}&offset={offset}&length=100"
        with urllib.request.urlopen(url, timeout=90) as resp:
            payload = json.load(resp)
        rows.extend(r["row"] for r in payload["rows"])
    return rows


def price_minor(up):
    """Exact pence from a <=2-decimal value, via Decimal (never float)."""
    d = decimal.Decimal(str(up)).quantize(decimal.Decimal("0.01"))
    return int((d * 100).to_integral_value())


def fmt_price(up):
    return f"{decimal.Decimal(str(up)).quantize(decimal.Decimal('0.01')):.2f}"


def cust_str(c):
    return "" if c is None else str(int(c))


def build_snapshot(rows):
    """Cut before the last (possibly truncated) invoice to guarantee that every
    vendored invoice is complete, and return the kept raw rows."""
    last_inv = rows[-1]["InvoiceNo"]
    cut = next(i for i, r in enumerate(rows) if r["InvoiceNo"] == last_inv)
    return rows[:cut]


def write_csv(snap, path):
    with open(path, "w", newline="") as fh:
        w = csv.writer(fh, quoting=csv.QUOTE_MINIMAL, lineterminator="\n")
        w.writerow(["InvoiceNo", "StockCode", "Description", "Quantity",
                    "InvoiceDate", "UnitPrice", "CustomerID", "Country"])
        for r in snap:
            w.writerow([r["InvoiceNo"], r["StockCode"], r["Description"], r["Quantity"],
                        r["InvoiceDate"], fmt_price(r["UnitPrice"]),
                        cust_str(r["CustomerID"]), r["Country"]])


def references(snap):
    """Pure-Python recomputation of every pinned value. Shaping rules identical
    to the Java loader: skip invoices with any blank CustomerID, skip zero-net
    invoices; a sale is net>0, a return is net<0."""
    by_invoice = OrderedDict()
    for r in snap:
        by_invoice.setdefault(r["InvoiceNo"], []).append(r)

    cust_balance = defaultdict(int)
    revenue = 0
    gross = 0
    entries = 0
    reversals = 0
    for inv, items in by_invoice.items():
        if any(it["CustomerID"] is None for it in items):
            continue
        cust = int(items[0]["CustomerID"])
        net = sum(it["Quantity"] * price_minor(it["UnitPrice"]) for it in items)
        if net == 0:
            continue
        entries += 1
        reversals += 1 if net < 0 else 0
        cust_balance[cust] += net
        revenue += net
        gross += abs(net)
    return {
        "line_items": len(snap),
        "entries": entries,
        "customers": len(cust_balance),
        "reversals": reversals,
        "revenue_minor": revenue,
        "customer_asset_total_minor": sum(cust_balance.values()),
        "trial_balance_net": sum(cust_balance.values()) - revenue,
        "gross_turnover_minor": gross,
        "customer_balances": dict(cust_balance),
    }


def main():
    rows = fetch_rows(ROWS)
    snap = build_snapshot(rows)
    if "--check" in sys.argv:
        import tempfile
        tmp = os.path.join(tempfile.gettempdir(), "online_retail_head.check.csv")
        write_csv(snap, tmp)
        a = open(tmp, "rb").read()
        b = open(CSV_PATH, "rb").read()
        print("byte-identical to committed snapshot:", a == b)
        sys.exit(0 if a == b else 1)

    write_csv(snap, CSV_PATH)
    ref = references(snap)
    print(f"wrote {CSV_PATH} ({os.path.getsize(CSV_PATH)} bytes)")
    print("---- pinned reference values (independent of the Java engine) ----")
    for k in ("line_items", "entries", "customers", "reversals",
              "revenue_minor", "customer_asset_total_minor", "trial_balance_net",
              "gross_turnover_minor"):
        print(f"  {k:28s}= {ref[k]}")
    for c in (17850, 13047, 17548, 15311, 14527):
        print(f"  customer {c:<8d} balance = {ref['customer_balances'].get(c)}")


if __name__ == "__main__":
    main()
