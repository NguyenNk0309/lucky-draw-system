export function LuckyWheel({
  spinning,
  result,
}: {
  spinning: boolean;
  result?: string;
}) {
  return (
    <div className="wheel-stage" aria-live="polite">
      <div className="wheel-pointer" aria-hidden="true" />
      <div
        className={`wheel ${spinning ? 'spinning' : ''}`}
        aria-label="Lucky draw wheel"
      >
        <div className="wheel-hub">★</div>
      </div>
      <strong className="wheel-result">
        {spinning ? 'Spinning…' : (result ?? 'Ready to spin')}
      </strong>
    </div>
  );
}
