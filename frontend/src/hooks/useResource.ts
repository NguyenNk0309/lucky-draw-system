import { useCallback, useEffect, useState } from 'react';

export function useResource<T>(
  loader: () => Promise<T>,
  dependencies: unknown[],
) {
  const [data, setData] = useState<T>();
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setData(await loader());
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Request failed');
    } finally {
      setLoading(false);
    }
    // The caller supplies the stable values that define this request.
    // eslint-disable-next-line react-hooks/exhaustive-deps, react-hooks/use-memo
  }, dependencies);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return { data, error, loading, refresh };
}
