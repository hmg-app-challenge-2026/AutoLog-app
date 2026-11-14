import type { PlaceKind } from './api'

export const PLACE_KINDS: { value: PlaceKind; label: string }[] = [
  { value: 'CLIENT', label: '거래처' },
  { value: 'BRANCH', label: '지점' },
  { value: 'OFFICE', label: '본사' },
  { value: 'GAS_STATION', label: '주유소' },
  { value: 'OTHER', label: '기타' },
]

/** 차 안 PurposeDetail과 같은 코드 (자택·확인 필요는 관리자가 고르지 않는다). */
export const PURPOSES: { value: string; label: string }[] = [
  { value: 'CLIENT_VISIT', label: '거래처 방문' },
  { value: 'MEETING', label: '회의' },
  { value: 'FIELD_WORK', label: '외근' },
  { value: 'BUSINESS_TRIP', label: '출장' },
  { value: 'BRANCH_MOVE', label: '지점 이동' },
]

export const kindLabel = (k: string) => PLACE_KINDS.find(x => x.value === k)?.label ?? k
export const purposeLabel = (p: string | null) => (p ? (PURPOSES.find(x => x.value === p)?.label ?? p) : '')
