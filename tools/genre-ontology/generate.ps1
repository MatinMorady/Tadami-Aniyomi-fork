# ============================================================
# GenreOntology generator (M1) - Tadami discovery
# Sources: Wikidata (labels/aliases by Q-ID) + Shikimori API (RU<->EN)
#          + curated manual aliases (manual-aliases.json)
#          + legacy dictionary MultilingualQueryHelper.kt (RU pairs).
# Output:  app/src/main/java/eu/kanade/tachiyomi/data/discovery/GenreOntology.kt
# Usage:   powershell -File tools\genre-ontology\generate.ps1 [-ReviewOnly]
# Data edits go ONLY here (overrides / seeds / manual-aliases.json), never into the generated file.
# NOTE: script is intentionally ASCII-only (PS 5.1 compatible); non-ASCII data
#       lives in manual-aliases.json / Wikidata / Shikimori / legacy dictionary.
# ============================================================
param([switch]$ReviewOnly)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$UA = @{ 'User-Agent' = 'TadamiGenreOntology/1.0 (generator)' }
$root = Split-Path $PSScriptRoot -Parent | Split-Path -Parent
$srcLegacy = Join-Path $root 'app\src\main\java\eu\kanade\tachiyomi\data\suggestions\MultilingualQueryHelper.kt'
$outKotlin = Join-Path $root 'app\src\main\java\eu\kanade\tachiyomi\data\discovery\GenreOntology.kt'
$manualJson = Join-Path $PSScriptRoot 'manual-aliases.json'

# ---------- Canonical seed (AniList/MAL/Shikimori taxonomies + novel domain) ----------
$seeds = @(
    'action','adventure','comedy','drama','fantasy','horror','mystery','romance','sci-fi',
    'slice of life','sports','supernatural','thriller','psychological','ecchi','mecha','music',
    'mahou shoujo','isekai','harem','reverse harem','yuri','yaoi','shounen ai','shoujo ai',
    'shounen','shoujo','seinen','josei','historical','military','school','vampire','zombies',
    'demons','martial arts','magic','tragedy','parody','gag','gender bender','crossdressing',
    'cooking','crime','iyashikei','space','superpowers','superheroes','mythology',
    'post-apocalyptic','cyberpunk','steampunk','dystopia','time travel','reincarnation',
    'virtual reality','game','hentai','gore','guro','bara','futanari'
)

# SPARQL class lookups for seeds whose name differs from the class label.
$seedSparqlAliases = @{
    'sci-fi'       = @('science fiction')
    'mahou shoujo' = @('magical girl')
}

# Forced Q-ID lists (skip auto search entirely) - deterministic, verified by review.
$qidForced = @{
    'psychological' = @('Q101240583')   # psychological anime and manga
    'sci-fi'        = @('Q189773', 'Q5366020')   # science fiction + sf anime and manga
    'shounen'       = @('Q231302')      # shonen demographic
    'shoujo'        = @('Q242492')      # shojo demographic
    'seinen'        = @('Q237338')
    'josei'         = @('Q503106')
    'shounen ai'    = @('Q756926')      # shounen-ai genre
    'shoujo ai'     = @('Q115069641')   # Girls' Love (anime/manga genre class item)
}

# Descriptions that indicate the search picked an unrelated entity (place, person, album...).
# Such a "broad" Q-ID is dropped; the seed falls back to SPARQL/manual/legacy aliases only.
$descBlocklist = 'settlement|village|district|given name|surname|album|song|single|organism|maturity|journal|painting|institution|educational|video game|film \d|company|plant|animal|character|span|blank|whitespace|municipality|census|river|island|lake|band|musical group|reggae'

# ---------- Normalization (mirror of GenreMatcher.normalize) ----------
function Normalize([string]$raw) {
    if ([string]::IsNullOrWhiteSpace($raw)) { return '' }
    $s = $raw.Trim().ToLowerInvariant()
    $d = $s.Normalize([Text.NormalizationForm]::FormD)
    $sb = [Text.StringBuilder]::new()
    foreach ($ch in $d.ToCharArray()) {
        $cat = [Globalization.CharUnicodeInfo]::GetUnicodeCategory($ch)
        if ($cat -eq [Globalization.UnicodeCategory]::NonSpacingMark) { continue }
        if ([Char]::IsLetterOrDigit($ch)) { [void]$sb.Append($ch) } else { [void]$sb.Append(' ') }
    }
    ($sb.ToString() -split '\s+' | Where-Object { $_ }) -join ' '
}

# ---------- JSON helpers (PS 5.1 compatible) ----------
function Read-JsonFile([string]$path) {
    if (-not (Test-Path $path)) { return $null }
    return ConvertFrom-Json ([IO.File]::ReadAllText($path, [Text.UTF8Encoding]::new($false)))
}
function Read-JsonMap([string]$path) {
    $obj = Read-JsonFile $path
    $map = @{}
    if ($obj) { foreach ($p in $obj.PSObject.Properties) { $map[$p.Name] = $p.Value } }
    return $map
}

# ---------- HTTP with retries/backoff ----------
function Invoke-Api([string]$uri) {
    foreach ($wait in @(0, 10, 30, 60, 90)) {
        if ($wait -gt 0) { Write-Host "  api busy, backoff ${wait}s"; Start-Sleep -Seconds $wait }
        try { return Invoke-RestMethod -Uri $uri -Headers $UA }
        catch {
            if ($wait -eq 90) { throw }
        }
    }
}

# ---------- 1. Resolve Q-IDs ----------
# Per seed: forced overrides -> SPARQL anime/manga genre class -> live search (broad item, cached).
# Multiple Q-IDs per canonical are allowed: broad genre items carry richer multilingual aliases.
$sparqlCachePath = Join-Path $PSScriptRoot '.sparql-cache.json'
$sparqlMap = Read-JsonMap $sparqlCachePath
if (-not $sparqlMap) {
    $q = [uri]::EscapeDataString("SELECT DISTINCT ?item ?enLabel WHERE { ?item wdt:P31/wdt:P279* wd:Q4178140 . ?item rdfs:label ?enLabel . FILTER(LANG(?enLabel)='en') } ORDER BY ?enLabel")
    $s = Invoke-Api "https://query.wikidata.org/sparql?query=$q&format=json"
    $sparqlMap = @{}
    foreach ($b in $s.results.bindings) {
        $qid = $b.item.value -replace '.*entity/', ''
        $n = Normalize ([string]$b.enLabel.value)
        if (-not $n) { continue }
        if (-not $sparqlMap.ContainsKey($n)) { $sparqlMap[$n] = $qid }
        # bare key without the class suffix ("action anime and manga" -> "action")
        $bare = $n -replace '\s+(anime and manga|anime or manga|anime|manga)$', ''
        if ($bare -and $bare -ne $n -and -not $sparqlMap.ContainsKey($bare)) { $sparqlMap[$bare] = $qid }
    }
    $sparqlMap | ConvertTo-Json -Depth 3 | Set-Content -Encoding UTF8 $sparqlCachePath
}
Write-Host "sparql anime/manga genre map: $($sparqlMap.Count) keys"

$qidCachePath = Join-Path $PSScriptRoot '.qid-cache.json'
$qidCache = Read-JsonMap $qidCachePath
function Save-QidCache { $qidCache | ConvertTo-Json -Depth 3 | Set-Content -Encoding UTF8 $qidCachePath }

function Resolve-Qid([string]$name) {
    $u = "https://www.wikidata.org/w/api.php?action=wbsearchentities&search=$([uri]::EscapeDataString($name))&language=en&format=json&limit=6"
    $r = Invoke-Api $u
    Start-Sleep -Milliseconds 2000
    $cands = @($r.search)
    if (-not $cands) { return $null }
    $best = $cands | Where-Object { $_.description -match 'anime and manga' } | Select-Object -First 1
    if (-not $best) { $best = $cands | Where-Object { $_.description -match 'genre|demograph' } | Select-Object -First 1 }
    if (-not $best) { $best = $cands | Where-Object { $_.label.ToLowerInvariant() -eq $name.ToLowerInvariant() } | Select-Object -First 1 }
    if (-not $best) { $best = $cands[0] }
    if ($best.description -match $descBlocklist) {
        Write-Host "  blocked junk: $name -> $($best.qid) ($($best.label): $($best.desc)) - skipped"
        return $null
    }
    return @{ qid = $best.id; label = $best.label; desc = [string]$best.description }
}

Write-Host "== 1. Resolve Q-IDs for $($seeds.Count) seeds =="
$qidMap = @{}   # canonical -> array of Q-IDs
foreach ($s in $seeds) {
    if ($qidForced.ContainsKey($s)) { $qidMap[$s] = @($qidForced[$s]); continue }
    $ids = @()
    foreach ($probe in @($s) + @($seedSparqlAliases[$s])) {
        $pn = Normalize $probe
        if ($pn -and $sparqlMap.ContainsKey($pn)) { $ids += $sparqlMap[$pn] }
    }
    if ($qidCache.ContainsKey($s)) {
        $ids += $qidCache[$s]
    } else {
        $hit = Resolve-Qid $s
        if ($hit) {
            $ids += $hit.qid
            $qidCache[$s] = $hit.qid
            Save-QidCache
            Write-Host "  search: $s -> $($hit.qid) ($($hit.label): $($hit.desc))"
        }
    }
    $ids = @($ids | Where-Object { $_ } | Sort-Object -Unique)
    if ($ids) { $qidMap[$s] = $ids }
}
Write-Host "resolved: $($qidMap.Count) / $($seeds.Count)"

# ---------- 2. Wikidata labels/aliases ----------
$langs = 'en|ru|uk|es|pt|fr|de|it|pl|nl|cs|tr|vi|id|th|ko|ja|zh|zh-hans|zh-hant|ar'
$qids = $qidMap.Values | ForEach-Object { $_ } | Sort-Object -Unique
$entityAliases = @{}   # qid -> @{ en; ru; aliases[] }
Write-Host "== 2. Wikidata: $($qids.Count) entities =="
for ($i = 0; $i -lt $qids.Count; $i += 40) {
    $batch = ($qids[$i..([Math]::Min($i + 39, $qids.Count - 1))]) -join '|'
    $u = "https://www.wikidata.org/w/api.php?action=wbgetentities&ids=$batch&props=labels|aliases&languages=$langs&format=json"
    $r = Invoke-Api $u
    Start-Sleep -Milliseconds 1500
    foreach ($p in $r.entities.PSObject.Properties) {
        $ent = $p.Value
        $enLabel = $null; $ruLabel = $null
        if ($ent.labels.en) { $enLabel = $ent.labels.en.value }
        if ($ent.labels.ru) { $ruLabel = $ent.labels.ru.value }
        $aliasList = @()
        if ($ent.aliases) {
            foreach ($lp in $ent.aliases.PSObject.Properties) {
                foreach ($a in @($lp.Value)) { $aliasList += [string]$a.value }
            }
        }
        $entityAliases[$p.Name] = @{ en = $enLabel; ru = $ruLabel; aliases = $aliasList }
    }
}
Write-Host "entities fetched: $($entityAliases.Count)"

# ---------- 3. Shikimori RU<->EN ----------
Write-Host "== 3. Shikimori genres =="
$shikiPairs = @{}   # normalized EN -> RU[]
foreach ($kind in @('anime','manga')) {
    try {
        $g = Invoke-Api "https://shikimori.one/api/genres?kind=$kind"
        Start-Sleep -Milliseconds 800
        foreach ($x in $g) {
            $enN = Normalize $x.name
            if ($enN -and $x.russian) {
                if (-not $shikiPairs.ContainsKey($enN)) { $shikiPairs[$enN] = @() }
                $shikiPairs[$enN] += [string]$x.russian
            }
        }
    } catch { Write-Host "shikimori $kind failed: $($_.Exception.Message)" }
}
Write-Host "shikimori pairs: $($shikiPairs.Count)"

# ---------- 4. Legacy dictionary (curated supplement) ----------
Write-Host "== 4. Legacy dictionary =="
$legacy = @{}   # normalized EN key -> RU variants
if (Test-Path $srcLegacy) {
    $txt = [IO.File]::ReadAllText($srcLegacy, [Text.UTF8Encoding]::new($false))
    foreach ($m in [regex]::Matches($txt, '"([^"]+)"\s+to\s+listOf\(([^)]*)\)')) {
        $en = Normalize $m.Groups[1].Value
        $ruList = [regex]::Matches($m.Groups[2].Value, '"([^"]+)"') | ForEach-Object { $_.Groups[1].Value }
        if ($en) { $legacy[$en] = @($ruList) }
    }
}
Write-Host "legacy pairs: $($legacy.Count)"

# ---------- 5. Manual aliases ----------
$manualAliases = Read-JsonMap $manualJson
Write-Host "manual groups: $($manualAliases.Count)"

# ---------- 6. Assemble the ontology ----------
Write-Host "== 6. Assemble =="
$display = @{}     # canonical -> @{en; ru}
$aliasIndex = @{}  # normalized alias -> HashSet[canonical]
function Add-Alias([string]$canonical, [string]$alias) {
    $n = Normalize $alias
    if (-not $n -or $n.Length -gt 64) { return }
    if (-not $aliasIndex.ContainsKey($n)) { $aliasIndex[$n] = [System.Collections.Generic.HashSet[string]]::new() }
    [void]$aliasIndex[$n].Add($canonical)
}
foreach ($s in $seeds) {
    $enDisp = $s; $ruDisp = $null
    foreach ($qid in @($qidMap[$s])) {
        if ($qid -and $entityAliases.ContainsKey($qid)) {
            $e = $entityAliases[$qid]
            if ($e.en -and $enDisp -eq $s) { $enDisp = $e.en }
            if (-not $ruDisp -and $e.ru) { $ruDisp = $e.ru }
            foreach ($a in $e.aliases) { Add-Alias $s $a }
            if ($e.en) { Add-Alias $s $e.en }
            if ($e.ru) { Add-Alias $s $e.ru }
        }
    }
    Add-Alias $s $s
    $sN = Normalize $s
    foreach ($probe in @($s) + @($seedSparqlAliases[$s])) {
        $pn = Normalize $probe
        if ($shikiPairs.ContainsKey($pn)) { foreach ($ru in $shikiPairs[$pn]) { Add-Alias $s $ru; if (-not $ruDisp) { $ruDisp = $ru } } }
        if ($legacy.ContainsKey($pn)) { foreach ($ru in $legacy[$pn]) { Add-Alias $s $ru; if (-not $ruDisp) { $ruDisp = $ru } } }
    }
    $display[$s] = @{ en = $enDisp; ru = $ruDisp }
}
foreach ($p in $manualAliases.Keys) {
    $c = $p
    if (-not $display.ContainsKey($c)) {
        $vals = @($manualAliases[$c])
        $ruFirst = $vals | Where-Object { $_ -match '\p{IsCyrillic}' } | Select-Object -First 1
        $display[$c] = @{ en = $c; ru = $ruFirst }
        Add-Alias $c $c
    }
    foreach ($a in @($manualAliases[$c])) { Add-Alias $c ([string]$a) }
}

$totalAliases = ($aliasIndex.Keys | Measure-Object).Count
Write-Host "canonical genres: $($display.Count); alias entries: $totalAliases"

# ---------- Review ----------
if ($ReviewOnly) {
    Write-Host "`n===== REVIEW: canonical genres ====="
    foreach ($s in ($display.Keys | Sort-Object)) {
        $d = $display[$s]
        $samples = ($aliasIndex.GetEnumerator() | Where-Object { $_.Value.Contains($s) } | Select-Object -First 6 | ForEach-Object { $_.Key }) -join ', '
        "{0,-20} en='{1}' ru='{2}'  [{3}]" -f $s, $d.en, $d.ru, $samples
    }
    Write-Host "`n(review mode: no file written)"
    exit 0
}

# ---------- 7. Emit Kotlin ----------
Write-Host "== 7. Emit Kotlin =="
$stamp = Get-Date -Format 'yyyy-MM-dd'
# Display strings are raw (not normalized): strip chars that would break Kotlin string literals.
function Clean-Disp([string]$s) {
    if ($null -eq $s) { return $null }
    return $s.Replace('"', ' ').Replace('$', ' ').Replace('`', ' ').Trim()
}
$aliasLines = $aliasIndex.Keys | Sort-Object | ForEach-Object {
    $k = $_; $vals = ($aliasIndex[$k] | Sort-Object) -join '", "'
    '            "@K@" to setOf("@V@"),' -replace '@K@', $k -replace '@V@', $vals
}
$dispLines = $display.Keys | Sort-Object | ForEach-Object {
    $k = $_; $en = Clean-Disp $display[$k].en; $ru = Clean-Disp $display[$k].ru
    $ruVal = if ($ru) { '"' + $ru + '"' } else { 'null' }
    '            "@K@" to ("@E@" to @RU@),' -replace '@K@', $k -replace '@E@', $en -replace '@RU@', $ruVal
}
$kt = @"
package eu.kanade.tachiyomi.data.discovery

/**
 * GENERATED by tools/genre-ontology/generate.ps1 ($stamp) - DO NOT EDIT BY HAND.
 * Sources: Wikidata labels/aliases (Q-IDs) + Shikimori API (RU<->EN) + legacy dictionary
 * MultilingualQueryHelper + curated manual aliases.
 * Canonical genres: $($display.Count), alias entries: $totalAliases.
 */
internal object GenreOntology {

    /** Canonical genre key -> (English display name, Russian display name|null) for the picker UI. */
    val displayNames: Map<String, Pair<String, String?>> = mapOf(
@DISP@
    )

    /** Normalized alias (any language) -> canonical genres. */
    val aliasIndex: Map<String, Set<String>> = mapOf(
@ALIASES@
    )
}
"@ -replace '@DISP@', ($dispLines -join "`n") -replace '@ALIASES@', ($aliasLines -join "`n")
[System.IO.File]::WriteAllText($outKotlin, $kt, [Text.UTF8Encoding]::new($false))
Write-Host "written: $outKotlin ($([math]::Round((Get-Item $outKotlin).Length/1KB)) KB)"
