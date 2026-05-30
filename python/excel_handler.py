"""
AutoBank Excel/PDF import handler. dashboard
Called by Java via: python excel_handler.py <file_path>
Returns JSON to stdout.
"""

import json
import sys
from pathlib import Path

import pandas as pd


def import_payment_file(file_path: str) -> dict:
    path = Path(file_path)
    if not path.exists():
        return {"error": f"File not found: {file_path}"}

    try:
        df = pd.read_excel(path) if path.suffix in (".xlsx", ".xls") else pd.read_csv(path)
        records = []
        for _, row in df.iterrows():
            records.append({
                "name": str(row.get("Name", row.get("नाव", ""))).strip(),
                "amount": float(row.get("Amount", row.get("रक्कम", 0)) or 0),
                "account_number": str(row.get("Account", row.get("खाते", ""))).strip(),
            })
        return {
            "total_records": len(records),
            "total_amount": sum(r["amount"] for r in records),
            "records": records,
        }
    except Exception as e:
        return {"error": str(e)}


def export_to_excel(data: list, output_path: str, sheet_name: str = "Report") -> dict:
    try:
        pd.DataFrame(data).to_excel(output_path, sheet_name=sheet_name, index=False)
        return {"success": True, "path": output_path}
    except Exception as e:
        return {"error": str(e)}


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"error": "Usage: excel_handler.py <file_path>"}))
        sys.exit(1)
    print(json.dumps(import_payment_file(sys.argv[1])))
