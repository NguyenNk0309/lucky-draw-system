import type { CSSProperties } from 'react';

export function LuckyWheel({
  spinning,
  result,
  reward,
  segment,
}: {
  spinning: boolean;
  result?: string;
  reward?: string;
  segment?: number;
}) {
  const labels = [
    'NO PRIZE',
    reward ?? 'LUCKY',
    'NO PRIZE',
    'NO PRIZE',
    'NO PRIZE',
    reward ?? 'LUCKY',
    'NO PRIZE',
    'NO PRIZE',
  ];
  const stop = 2160 - ((segment ?? 0) * 45 + 22.5);
  return (
    <div className="wheel-stage" aria-live="polite">
      <div className="wheel-pointer" aria-hidden="true" />
      <div
        className={`wheel ${spinning ? 'spinning' : segment === undefined ? '' : 'settled'}`}
        style={{ '--wheel-stop': `${stop}deg` } as CSSProperties}
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
