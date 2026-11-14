import { useEffect, useState, type FormEvent } from 'react'
import { api, ApiError, type Place, type PlaceCandidate, type PlaceKind, type Purpose, type PurposeCategory, type PurposeRule } from './api'
import { PLACE_KINDS } from './labels'
import { ErrorBox } from './ErrorBox'

/**
 * 목적 (F-08). 회사 목적을 추가하고, "출발 장소 → 도착 장소 = 목적" 규칙을 정한다.
 * 바꾸면 연결된 모든 차와 새로 연결하는 차에 들어간다. 차는 운행이 끝나면 출발·도착이 둘 다 맞는 규칙의 목적을 먼저 추천한다.
 */

const CATEGORIES: { value: PurposeCategory; label: string }[] = [
  { value: 'BUSINESS', label: '일반 업무용' },
  { value: 'COMMUTE', label: '출퇴근용' },
  { value: 'NON_BUSINESS', label: '비업무용' },
]
const categoryLabel = (c: string) => CATEGORIES.find(x => x.value === c)?.label ?? c

/** 출발지 고르기에서 "어디서든"을 뜻하는 값. */
const ANYWHERE = ''

export function PurposesScreen() {
  const [purposes, setPurposes] = useState<Purpose[]>([])
  const [rules, setRules] = useState<PurposeRule[]>([])
  const [places, setPlaces] = useState<Place[]>([])
  const [error, setError] = useState<ApiError | null>(null)

  const load = () =>
    Promise.all([api.purposes(), api.purposeRules(), api.places()])
      .then(([p, r, pl]) => {
        setPurposes(p)
        setRules(r)
        setPlaces(pl)
      })
      .catch(err => setError(err as ApiError))

  useEffect(() => {
    load()
  }, [])

  return (
    <section>
      <header className="screen-head">
        <div>
          <h2>목적</h2>
          <p className="muted">
            회사 목적과 출발→도착 규칙이에요. 여기서 바꾸면 연결된 모든 차에 자동으로 들어가요.
          </p>
        </div>
      </header>
      <ErrorBox error={error} />
      <PurposeList purposes={purposes} onChanged={load} />
      <RuleList rules={rules} purposes={purposes} places={places} onChanged={load} />
    </section>
  )
}

function PurposeList({ purposes, onChanged }: { purposes: Purpose[]; onChanged: () => void }) {
  const [form, setForm] = useState<{ label: string; category: PurposeCategory }>({ label: '', category: 'BUSINESS' })
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  const add = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!form.label.trim()) return setError(new ApiError(400, 'FORM', '목적 이름을 입력해 주세요'))
    setBusy(true)
    try {
      await api.addPurpose(form.label.trim(), form.category)
      setForm({ ...form, label: '' })
      onChanged()
    } catch (err) {
      setError(err as ApiError)
    } finally {
      setBusy(false)
    }
  }

  const remove = async (p: Purpose) => {
    if (p.id == null) return
    if (!confirm(`'${p.label}' 목적을 지울까요?\n이 목적을 쓰는 규칙도 함께 지워져요. 이미 기록된 운행의 목적은 그대로 남아요.`)) return
    try {
      await api.deletePurpose(p.id)
      onChanged()
    } catch (err) {
      setError(err as ApiError)
    }
  }

  return (
    <>
      <h3 style={{ margin: '8px 0' }}>목적 목록</h3>
      <form className="card form" onSubmit={add}>
        <div className="row">
          <label className="field grow">
            <span>목적 이름</span>
            <input placeholder="예: 납품" maxLength={30} value={form.label} onChange={e => setForm({ ...form, label: e.target.value })} />
          </label>
          <label className="field">
            <span>운행기록부 구분</span>
            <select value={form.category} onChange={e => setForm({ ...form, category: e.target.value as PurposeCategory })}>
              {CATEGORIES.map(c => (
                <option key={c.value} value={c.value}>
                  {c.label}
                </option>
              ))}
            </select>
          </label>
          <button type="submit" className="button" disabled={busy}>
            {busy ? '추가 중…' : '목적 추가'}
          </button>
        </div>
        <ErrorBox error={error} />
      </form>
      <table className="table" style={{ marginBottom: 28 }}>
        <thead>
          <tr>
            <th>목적</th>
            <th>구분</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {purposes.map(p => (
            <tr key={p.name}>
              <td>{p.label}</td>
              <td>{categoryLabel(p.category)}</td>
              <td className="right">
                {p.builtIn ? (
                  <span className="chip">기본</span>
                ) : (
                  <button className="link danger" onClick={() => remove(p)}>
                    지우기
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  )
}

function RuleList({
  rules,
  purposes,
  places,
  onChanged,
}: {
  rules: PurposeRule[]
  purposes: Purpose[]
  places: Place[]
  onChanged: () => void
}) {
  const [form, setForm] = useState({ origin: ANYWHERE, dest: '', purpose: '' })
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)
  const [newPlace, setNewPlace] = useState<'origin' | 'dest' | null>(null)

  const placeLabel = (id: string | null) =>
    id == null ? '어디서든' : (places.find(p => p.placeId === id)?.label ?? '(지운 장소)')
  const purposeLabel = (name: string) => purposes.find(p => p.name === name)?.label ?? name.split(':').pop()

  const add = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!form.dest) return setError(new ApiError(400, 'FORM', '도착 장소를 골라 주세요'))
    if (!form.purpose) return setError(new ApiError(400, 'FORM', '목적을 골라 주세요'))
    setBusy(true)
    try {
      await api.addPurposeRule({ originPlaceId: form.origin || null, destPlaceId: form.dest, purpose: form.purpose })
      setForm({ origin: ANYWHERE, dest: '', purpose: '' })
      onChanged()
    } catch (err) {
      setError(err as ApiError)
    } finally {
      setBusy(false)
    }
  }

  const remove = async (r: PurposeRule) => {
    if (!confirm(`${placeLabel(r.originPlaceId)} → ${placeLabel(r.destPlaceId)} 규칙을 지울까요?`)) return
    try {
      await api.deletePurposeRule(r.ruleId)
      onChanged()
    } catch (err) {
      setError(err as ApiError)
    }
  }

  return (
    <>
      <h3 style={{ margin: '8px 0' }}>출발 → 도착 규칙</h3>
      <p className="muted small" style={{ marginBottom: 12 }}>
        출발 장소와 도착 장소가 둘 다 맞으면 그 목적으로 추천해요. 출발을 "어디서든"으로 두면 도착 장소만 봐요. 장소는 [장소]
        화면에서 등록해요.
      </p>
      <form className="card form" onSubmit={add}>
        <div className="row">
          <label className="field grow">
            <span>출발</span>
            <select value={form.origin} onChange={e => setForm({ ...form, origin: e.target.value })}>
              <option value={ANYWHERE}>어디서든</option>
              {places.map(p => (
                <option key={p.placeId} value={p.placeId}>
                  {p.label}
                </option>
              ))}
            </select>
          </label>
          <label className="field grow">
            <span>도착</span>
            <select value={form.dest} onChange={e => setForm({ ...form, dest: e.target.value })}>
              <option value="">고르세요</option>
              {places.map(p => (
                <option key={p.placeId} value={p.placeId}>
                  {p.label}
                </option>
              ))}
            </select>
          </label>
          <label className="field grow">
            <span>목적</span>
            <select value={form.purpose} onChange={e => setForm({ ...form, purpose: e.target.value })}>
              <option value="">고르세요</option>
              {purposes.map(p => (
                <option key={p.name} value={p.name}>
                  {p.label} ({categoryLabel(p.category)})
                </option>
              ))}
            </select>
          </label>
          <button type="submit" className="button" disabled={busy}>
            {busy ? '추가 중…' : '규칙 추가'}
          </button>
        </div>
        <div className="row">
          <button type="button" className="link" onClick={() => setNewPlace('origin')}>
            + 새 출발 장소
          </button>
          <button type="button" className="link" onClick={() => setNewPlace('dest')}>
            + 새 도착 장소
          </button>
        </div>
        <ErrorBox error={error} />
      </form>
      {newPlace && (
        <NewPlacePanel
          target={newPlace}
          onClose={() => setNewPlace(null)}
          onCreated={place => {
            setNewPlace(null)
            setForm(f => (newPlace === 'origin' ? { ...f, origin: place.placeId } : { ...f, dest: place.placeId }))
            onChanged()
          }}
        />
      )}
      <table className="table">
        <thead>
          <tr>
            <th>출발</th>
            <th>도착</th>
            <th>목적</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {rules.length === 0 && (
            <tr>
              <td colSpan={4} className="empty">
                아직 규칙이 없어요
              </td>
            </tr>
          )}
          {rules.map(r => (
            <tr key={r.ruleId}>
              <td>{placeLabel(r.originPlaceId)}</td>
              <td>{placeLabel(r.destPlaceId)}</td>
              <td>{purposeLabel(r.purpose)}</td>
              <td className="right">
                <button className="link danger" onClick={() => remove(r)}>
                  지우기
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  )
}

/** 규칙의 [+ 새 장소]: 이름·주소로 찾아(카카오) 하나 고르고 이름·종류를 붙여 장소로 등록한다. */
function NewPlacePanel({
  target,
  onClose,
  onCreated,
}: {
  target: 'origin' | 'dest'
  onClose: () => void
  onCreated: (p: Place) => void
}) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<PlaceCandidate[] | null>(null)
  const [picked, setPicked] = useState<number | null>(null)
  const [label, setLabel] = useState('')
  const [kind, setKind] = useState<PlaceKind>('OTHER')
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  const search = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (query.trim().length < 2) return setError(new ApiError(400, 'FORM', '두 글자 이상 입력해 주세요'))
    setBusy(true)
    try {
      const r = await api.searchPlaces(query.trim())
      setResults(r.results)
      setPicked(null)
    } catch (err) {
      setError(err as ApiError)
    } finally {
      setBusy(false)
    }
  }

  const pick = (i: number) => {
    setPicked(i)
    if (results) setLabel(results[i].name)
  }

  const create = async () => {
    if (picked == null || !results) return
    if (!label.trim()) return setError(new ApiError(400, 'FORM', '장소 이름을 입력해 주세요'))
    const c = results[picked]
    setBusy(true)
    setError(null)
    try {
      const place = await api.createPlace({ label: label.trim(), kind, lat: c.lat, lng: c.lng })
      onCreated(place)
    } catch (err) {
      setError(err as ApiError)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="card form" style={{ marginBottom: 16 }}>
      <div className="row between">
        <strong>새 {target === 'origin' ? '출발' : '도착'} 장소</strong>
        <button type="button" className="link" onClick={onClose}>
          닫기
        </button>
      </div>
      <form className="row" onSubmit={search}>
        <label className="field grow">
          <span>이름이나 주소로 찾기</span>
          <input placeholder="예: 판교 물류센터, 판교역로 166" value={query} onChange={e => setQuery(e.target.value)} autoFocus />
        </label>
        <button type="submit" className="button secondary" disabled={busy}>
          찾기
        </button>
      </form>
      {results && results.length === 0 && <p className="muted">찾은 곳이 없어요. 다른 이름이나 주소로 찾아 주세요</p>}
      {results && results.length > 0 && (
        <table className="table">
          <tbody>
            {results.map((r, i) => (
              <tr key={`${r.lat},${r.lng},${i}`} onClick={() => pick(i)} style={{ cursor: 'pointer' }}>
                <td style={{ width: 28 }}>
                  <input type="radio" checked={picked === i} onChange={() => pick(i)} />
                </td>
                <td>
                  <strong>{r.name}</strong>
                  {r.address && r.address !== r.name && <div className="muted small">{r.address}</div>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {picked != null && (
        <div className="row">
          <label className="field grow">
            <span>장소 이름</span>
            <input value={label} maxLength={30} onChange={e => setLabel(e.target.value)} />
          </label>
          <label className="field">
            <span>종류</span>
            <select value={kind} onChange={e => setKind(e.target.value as PlaceKind)}>
              {PLACE_KINDS.map(k => (
                <option key={k.value} value={k.value}>
                  {k.label}
                </option>
              ))}
            </select>
          </label>
          <button type="button" className="button" disabled={busy} onClick={create}>
            {busy ? '등록 중…' : '장소 등록'}
          </button>
        </div>
      )}
      <ErrorBox error={error} />
    </div>
  )
}
