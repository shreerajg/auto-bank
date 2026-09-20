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

try:
    import pytesseract
    OCR_AVAILABLE = True
except ImportError:
    pytesseract = None
    OCR_AVAILABLE = False


def extract_text_ocr(path: Path) -> str:
    """Extract text from a PDF using Tesseract OCR (pytesseract handles PDF natively)."""
    return pytesseract.image_to_string(str(path), lang="eng+mar")


def import_pdf_file(file_path: str) -> dict:
    path = Path(file_path)
    if not path.exists():
        return {"error": f"File not found: {file_path}"}

    if fitz is None and not OCR_AVAILABLE:
        return {
            "error": (
                "Neither PyMuPDF (fitz) nor Tesseract OCR is installed. "
                "Install one of: pip install PyMuPDF   OR   pip install pytesseract "
                "(requires Tesseract OCR engine installed on the system)."
            )
        }

    try:
        if fitz is not None:
            doc = fitz.open(path)
            chunks = []
            for page in doc:
                chunks.append(page.get_text())
            text = "\n".join(chunks)
        else:
            text = extract_text_ocr(path)

        lines = text.split("\n")
        records = []

        # Simple heuristic extraction - looking for Account, Name, Amount
        # Assuming a line format like: "12345 John Doe 1500.00"
        # or lines with Marathi names (Unicode \u0900-\u097F)
        for line in lines:
            line = line.strip()
            if not line:
                continue

            # Regex for: <AccountDigits> <Space> <Names> <Space> <AmountDigits>
            # e.g., "1023 श्रीकांत पाटील 5000.50" or "1023 Jane Doe 1,500.50"
            match = re.match(r'^(\d+)\s+(.+?)\s+([\d\.,]+)$', line)
            if match:
                account = match.group(1).strip()
                name = match.group(2).strip()
                try:
                    amount_str = match.group(3).strip().replace(',', '')
                    amount = float(amount_str)
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
