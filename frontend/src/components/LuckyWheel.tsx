import type { CSSProperties } from 'react';

export function LuckyWheel({
  spinning,
  result,
  reward,
}: {
  spinning: boolean;
  result?: string;
  reward?: string;
}) {
  const labels = [
    'ENTRY',
    reward ?? 'LUCKY',
    'LUCKY',
    'ENTRY',
    'ENTRY',
    reward ?? 'LUCKY',
    'LUCKY',
    'ENTRY',
  ];
  return (
    <div className="wheel-stage" aria-live="polite">
      <div className="wheel-pointer" aria-hidden="true" />
      <div
        className={`wheel ${spinning ? 'spinning' : ''}`}
        aria-label={
          reward ? `Lucky draw wheel with reward ${reward}` : 'Lucky draw wheel'
        }
      >
        <div className="wheel-labels" aria-hidden="true">
          {labels.map((label, index) => (
            <span
              className={`wheel-label ${label === reward ? 'prize' : ''}`}
              key={`${label}-${index}`}
              style={{ '--segment': index } as CSSProperties}
            >
              {label}
            </span>
          ))}
        </div>
        <div className="wheel-hub">★</div>
      </div>
      <strong className="wheel-result">
        {spinning ? 'Spinning…' : (result ?? 'Ready to spin')}
      </strong>
    </div>
  );
}
