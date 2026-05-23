"""
AutoBank PDF/Excel Report Generator.
Generates professional banking reports using ReportLab.
"""

import json
import sys
from datetime import datetime
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import cm
from reportlab.platypus import SimpleDocTemplate, Table, TableStyle, Paragraph, Spacer

def generate_pdf(data, output_path, title):
    doc = SimpleDocTemplate(output_path, pagesize=A4, rightMargin=2*cm, leftMargin=2*cm, topMargin=2*cm, bottomMargin=2*cm)
    elements = []
    styles = getSampleStyleSheet()

    # Title
    elements.append(Paragraph(f"AutoBank - {title}", styles['Title']))
    elements.append(Paragraph(f"Generated on: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}", styles['Normal']))
    elements.append(Spacer(1, 12))

    # Table Data
    headers = data['headers']
    rows = data['rows']
    table_data = [headers] + rows

    # Create Table
    table = Table(table_data, hAlign='LEFT')
    
    # Style
    style = TableStyle([
        ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#2c3e50')),
        ('TEXTCOLOR', (0, 0), (-1, 0), colors.whitesmoke),
        ('ALIGN', (0, 0), (-1, -1), 'CENTER'),
        ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
        ('FONTSIZE', (0, 0), (-1, 0), 12),
        ('BOTTOMPADDING', (0, 0), (-1, 0), 12),
        ('BACKGROUND', (0, 1), (-1, -1), colors.beige),
        ('GRID', (0, 0), (-1, -1), 1, colors.grey),
        ('FONTSIZE', (0, 1), (-1, -1), 10),
        ('ALIGN', (0, 1), (-1, -1), 'LEFT'),
    ])
    
    # Add currency alignment for balance columns if present
    for i, h in enumerate(headers):
        if "Balance" in h or "Amount" in h:
            style.add('ALIGN', (i, 1), (i, -1), 'RIGHT')

    table.setStyle(style)
    elements.append(table)
    
    doc.build(elements)
    return {"success": True, "path": output_path}

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print(json.dumps({"error": "Usage: report_generator.py <json_data_path> <output_path> <title>"}))
        sys.exit(1)
        
    try:
        with open(sys.argv[1], 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        result = generate_pdf(data, sys.argv[2], sys.argv[3])
        print(json.dumps(result))
    except Exception as e:
        print(json.dumps({"error": str(e)}))
