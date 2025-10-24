$ast = [System.Management.Automation.Language.Parser]::ParseFile("producer-flow-test.ps1", [ref]$null, [ref]$null); if ($ast) { "Syntax OK" } else { "Syntax Error" }
