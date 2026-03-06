export function getOrCreateDeviceId(): string {
  const existing = localStorage.getItem('device_id')
  if (existing) return existing
  const uuid = crypto.randomUUID()
  localStorage.setItem('device_id', uuid)
  return uuid
}
