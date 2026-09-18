"""
AutoBank PDF Dairy Import Handler.
Called by Java via: python pdf_importer.py <file_path>
Extracts payment records from Dairy PDF reports.
Returns JSON to stdout.
"""

import json
import sys
from pathlib import Path
import re

try:
    import fitz  # PyMuPDF
except ImportError:
    fitz = None

def import_pdf_file(file_path: str) -> dict:
    if fitz is None:
        return {"error": "PyMuPDF (fitz) is not installed. Please run: pip install PyMuPDF"}

    path = Path(file_path)
    if not path.exists():
        return {"error": f"File not found: {file_path}"}
    
    try:
        doc = fitz.open(path)
        records = []
        
        for page in doc:
            text = page.get_text()
            lines = text.split('\n')
            
            # Simple heuristic extraction - looking for Account, Name, Amount
            # Assuming a line format like: "12345 John Doe 1500.00" 
            # or lines with Marathi names (Unicode \u0900-\u097F)
            for line in lines:
                line = line.strip()
                if not line:
                    continue
                
                # Regex for: <AccountDigits> <Space> <Names> <Space> <AmountDigits>
                # e.g., "1023 श्रीकांत पाटील 5000.50"
                match = re.match(r'^(\d+)\s+(.+?)\s+([\d\.]+)$', line)
                if match:
                    account = match.group(1).strip()
                    name = match.group(2).strip()
                    try:
                        amount = float(match.group(3).strip())
                        records.append({
                            "account_number": account,
                            "name": name,
                            "amount": amount
                        })
                    except ValueError:
                        pass
                        
        return {
            "total_records": len(records),
            "total_amount": sum(r["amount"] for r in records),
            "records": records,
        }
    except Exception as e:
        return {"error": str(e)}

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"error": "Usage: python pdf_importer.py <file_path>"}))
        sys.exit(1)
    print(json.dumps(import_pdf_file(sys.argv[1])))
