"""
AutoBank Excel/PDF import handler. dashboard
Called by Java via: python excel_handler.py <file_path>
Returns JSON to stdout.
"""

import json
import sys
import math
from pathlib import Path

import pandas as pd


def validate_record(record: dict) -> dict | None:
    """Validate a single imported record. Return cleaned record or None if invalid."""
    name = str(record.get("name", "")).strip()
    account = str(record.get("account_number", "")).strip()
    try:
        amount = float(record.get("amount", 0) or 0)
    except (ValueError, TypeError):
        amount = 0.0
    if name == "":
        return None
    if account == "":
        return None
    if math.isnan(amount) or math.isinf(amount):
        amount = 0.0
    amount = abs(amount)
    return {"name": name, "amount": amount, "account_number": account}


def import_payment_file(file_path: str) -> dict:
    path = Path(file_path)
    if not path.exists():
        return {"error": f"File not found: {file_path}"}

    try:
        df = pd.read_excel(path) if path.suffix in (".xlsx", ".xls") else pd.read_csv(path)
    except Exception as e:
        return {"error": str(e)}

    records = []
    rejected_count = 0
    rejected_total = 0.0
    for _, row in df.iterrows():
        raw = {
            "name": str(row.get("Name", row.get("नाव", ""))),
            "amount": row.get("Amount", row.get("रक्कम", 0)),
            "account_number": str(row.get("Account", row.get("खाते", ""))),
        }
        validated = validate_record(raw)
        if validated is None:
            rejected_count += 1
            try:
                rejected_total += float(raw.get("amount", 0) or 0)
            except (ValueError, TypeError):
                pass
        else:
            records.append(validated)

    return {
        "total_records": len(records) + rejected_count,
        "rejected_records": rejected_count,
        "total_amount": sum(r["amount"] for r in records),
        "rejected_total": rejected_total,
        "records": records,
    }


def export_to_excel(data: dict, output_path: str, sheet_name: str = "Report") -> dict:
    try:
        if isinstance(data, dict) and "headers" in data and "rows" in data:
            records = [dict(zip(data["headers"], row)) for row in data["rows"]]
            df = pd.DataFrame(records)
        else:
            df = pd.DataFrame(data)

        df.to_excel(output_path, sheet_name=sheet_name, index=False)
        return {"success": True, "path": output_path}
    except Exception as e:
        return {"error": str(e)}


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"error": "Usage: excel_handler.py <file_path>"}))
        sys.exit(1)
    print(json.dumps(import_payment_file(sys.argv[1])))
