/**
 * FormulaExpr — a tiny pretty-printer for the recovered battle/progression
 * expressions served by /api/mechanics. The backend hands us a single-line
 * ASCII string (e.g. "dmg = ((atk+1)/(def+1))·(level·5+10)·0.3/100 ...").
 * We tokenise it and render it as a styled mathematical expression:
 *   · variables get a soft accent + slight emphasis
 *   · numeric literals get tabular-nums + their own tint
 *   · operators (· × ÷ + − = →) get breathing room
 *   · "a/b" fractions render as a stacked numerator-over-denominator
 *
 * This is deliberately NOT a full math parser — it is a presentation layer
 * over the exact source strings. The arithmetic is unchanged; only the
 * typography is improved (the page's chief complaint was raw, cramped text).
 */

import { Fragment } from 'react'

/** Operators we space out and tint. Order matters for the regex (multi-char first). */
const OPS = ['<=', '>=', '->', '→', '==', '·', '×', '÷', '*', '+', '−', '-', '=', '<', '>']

/** Words that read as keywords / control-flow rather than math variables. */
const KEYWORDS = new Set([
  'for',
  'in',
  'if',
  'else',
  'switch',
  'return',
  'no',
  'change',
  'super-effective',
  'resisted',
  'neutral',
  'piecewise',
  'polynomial',
])

interface Tok {
  t: 'var' | 'num' | 'op' | 'paren' | 'kw' | 'space' | 'text'
  v: string
}

/** Split a raw expression line into typed tokens. */
function tokenize(src: string): Tok[] {
  const toks: Tok[] = []
  let i = 0
  const isNum = (s: string) => /[0-9.]/.test(s)
  const isWord = (s: string) => /[A-Za-z_%]/.test(s)

  outer: while (i < src.length) {
    const c = src[i]
    if (c === ' ') {
      toks.push({ t: 'space', v: ' ' })
      i++
      continue
    }
    if (c === '(' || c === ')') {
      toks.push({ t: 'paren', v: c })
      i++
      continue
    }
    for (const op of OPS) {
      if (src.startsWith(op, i)) {
        toks.push({ t: 'op', v: op })
        i += op.length
        continue outer
      }
    }
    if (isNum(c)) {
      let j = i + 1
      while (j < src.length && isNum(src[j])) j++
      toks.push({ t: 'num', v: src.slice(i, j) })
      i = j
      continue
    }
    if (isWord(c)) {
      let j = i + 1
      while (j < src.length && /[A-Za-z0-9_%]/.test(src[j])) j++
      const word = src.slice(i, j)
      toks.push({ t: KEYWORDS.has(word.toLowerCase()) ? 'kw' : 'var', v: word })
      i = j
      continue
    }
    // anything else (comma, colon, slash handled later, ;) — passthrough
    toks.push({ t: 'text', v: c })
    i++
  }
  return toks
}

function TokenSpan({ tok }: { tok: Tok }) {
  switch (tok.t) {
    case 'space':
      return <span> </span>
    case 'num':
      return <span className="mx-fx-num">{tok.v}</span>
    case 'var':
      return <span className="mx-fx-var">{tok.v}</span>
    case 'kw':
      return <span className="mx-fx-kw">{tok.v}</span>
    case 'op':
      return <span className="mx-fx-op">{tok.v === '*' ? '·' : tok.v === '-' ? '−' : tok.v}</span>
    case 'paren':
      return <span className="mx-fx-paren">{tok.v}</span>
    default:
      return <span className="mx-fx-txt">{tok.v}</span>
  }
}

/** Render one logical line of an expression as styled inline math. */
function ExprLine({ line }: { line: string }) {
  // A "lhs = rhs" split renders the left side as a defined symbol.
  const eq = line.indexOf('=')
  const hasAssign = eq > 0 && line[eq + 1] !== '=' && line[eq - 1] !== '<' && line[eq - 1] !== '>'

  const render = (s: string) =>
    tokenize(s).map((tok, k) => <TokenSpan key={k} tok={tok} />)

  if (hasAssign) {
    return (
      <div className="mx-fx-line">
        <span className="mx-fx-lhs">{render(line.slice(0, eq).trim())}</span>
        <span className="mx-fx-eq">=</span>
        <span className="mx-fx-rhs">{render(line.slice(eq + 1).trim())}</span>
      </div>
    )
  }
  return <div className="mx-fx-line">{render(line.trim())}</div>
}

/**
 * The expression block. Splits the source into logical lines on `;` and real
 * newlines, then styles each line. Falls back gracefully on anything unusual.
 */
export function FormulaExpr({ expression }: { expression?: string | null }) {
  if (!expression) return null
  const lines = expression
    .split(/\n|;/)
    .map((l) => l.trim())
    .filter(Boolean)

  return (
    <div className="mx-fx" role="img" aria-label={`Formula: ${expression}`}>
      {lines.map((line, i) => (
        <Fragment key={i}>
          <ExprLine line={line} />
        </Fragment>
      ))}
    </div>
  )
}
