# Export DOCX to PDF with Microsoft Word (Windows) and report pages and content-control character counts.
# Usage: powershell -ExecutionPolicy Bypass -File export_pdf_word.ps1 -Folder <folder with DOCX>
# ASCII only on purpose: Windows PowerShell 5.1 reads BOM-less UTF-8 scripts in the ANSI code page.
param([Parameter(Mandatory = $true)][string]$Folder)
$ErrorActionPreference = "Stop"
$word = New-Object -ComObject Word.Application
$word.Visible = $false
$word.DisplayAlerts = 0
try {
  "Word " + $word.Version + " (Build " + $word.Build + ")"
  foreach ($f in Get-ChildItem -LiteralPath $Folder -Filter *.docx) {
    $doc = $word.Documents.Open($f.FullName, $false, $true)
    try {
      $doc.Repaginate()
      "== " + $f.Name
      "  pages=" + $doc.ComputeStatistics(2) + " inlineShapes=" + $doc.InlineShapes.Count
      foreach ($cc in $doc.ContentControls) {
        if ($cc.Tag) { "    {0,-22} chars={1,6} page={2}" -f $cc.Tag, $cc.Range.ComputeStatistics(5), $cc.Range.Information(3) }
      }
      $pdf = [System.IO.Path]::ChangeExtension($f.FullName, ".pdf")
      # 17 = wdExportFormatPDF, OptimizeFor 0 = print, Range 0 = all, Item 0 = content, CreateBookmarks 1 = headings
      $doc.ExportAsFixedFormat($pdf, 17, $false, 0, 0, 0, 0, 0, $true, $true, 1, $true, $true, $false)
      "  pdf=" + (Split-Path $pdf -Leaf)
    } finally { $doc.Close(0) | Out-Null }
  }
} finally {
  $word.Quit() | Out-Null
  [System.Runtime.InteropServices.Marshal]::ReleaseComObject($word) | Out-Null
}
