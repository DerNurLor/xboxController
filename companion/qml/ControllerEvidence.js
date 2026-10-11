.pragma library

// Combine only for presentation. Source controls must never become OS input.
function combine(output, source) {
    var result = (output || [0,8,0,0,0,0,0,0]).slice()
    if (!source) return result
    result[0] |= source[0]
    if (source[1] !== 8) result[1] = source[1]
    for (var i=2; i<6; ++i)
        if (Math.abs(source[i]) > Math.abs(result[i])) result[i] = source[i]
    result[6] = Math.max(result[6], source[6])
    result[7] = Math.max(result[7], source[7])
    return result
}
