export function ErrorNotice({ message }: { message: string }) {
  return message ? (
    <p className="notice error" role="alert">
      {message}
    </p>
  ) : null;
}
export function Loading({ active }: { active: boolean }) {
  return active ? <p className="muted">Loading…</p> : null;
}
export function Empty({ show, children }: { show: boolean; children: string }) {
  return show ? <p className="muted">{children}</p> : null;
}
