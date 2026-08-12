import { useCallback, useEffect, useRef, useState } from 'react';

export function useResource<T>(
  loader: () => Promise<T>,
  dependencies: unknown[],
) {
  const [data, setData] = useState<T>();
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const requestId = useRef(0);

  const refresh = useCallback(async () => {
    const currentRequest = ++requestId.current;
    setLoading(true);
    setError('');
    setData(undefined);
    try {
      const value = await loader();
      if (currentRequest === requestId.current) setData(value);
    } catch (reason) {
      if (currentRequest === requestId.current) {
        setError(reason instanceof Error ? reason.message : 'Request failed');
      }
    } finally {
      if (currentRequest === requestId.current) setLoading(false);
    }
    // The caller supplies the stable values that define this request.
    // eslint-disable-next-line react-hooks/exhaustive-deps, react-hooks/use-memo
  }, dependencies);

  useEffect(() => {
    void refresh();
    return () => {
      requestId.current += 1;
    };
  }, [refresh]);

  return { data, error, loading, refresh };
}
