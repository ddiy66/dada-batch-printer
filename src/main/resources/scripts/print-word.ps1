param(
    [Parameter(Mandatory=$true)][string]$DocumentPath,
    [Parameter(Mandatory=$true)][string]$PrinterName,
    [Parameter(Mandatory=$true)][int]$Copies,
    [Parameter(Mandatory=$true)][ValidateSet('AUTO','PORTRAIT','LANDSCAPE')][string]$Orientation
)
$ErrorActionPreference = 'Stop'
$word = $null
$document = $null
try {
    $word = New-Object -ComObject Word.Application
    $word.Visible = $false
    $word.DisplayAlerts = 0
    $word.ActivePrinter = $PrinterName
    $document = $word.Documents.Open($DocumentPath, $false, $true)
    if ($Orientation -eq 'PORTRAIT') { $document.PageSetup.Orientation = 0 }
    if ($Orientation -eq 'LANDSCAPE') { $document.PageSetup.Orientation = 1 }
    $document.PrintOut($false, $false, 0, '', '', '', 0, $Copies)
} catch {
    [Console]::Error.WriteLine($_.Exception.Message)
    exit 1
} finally {
    if ($null -ne $document) { $document.Close(0) }
    if ($null -ne $word) { $word.Quit() }
    if ($null -ne $document) { [void][Runtime.InteropServices.Marshal]::ReleaseComObject($document) }
    if ($null -ne $word) { [void][Runtime.InteropServices.Marshal]::ReleaseComObject($word) }
    [GC]::Collect()
    [GC]::WaitForPendingFinalizers()
}
