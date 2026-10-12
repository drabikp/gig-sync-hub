import { Navigate } from 'react-router';
import { Loading } from '../components/Loading';
import { useMyBands } from '../api/hooks';
import { bandAddress, lastBand } from './band';

/** The start: the band used last on this device, else the first of the user's bands, else making one. */
export function StartPage() {
  const bands = useMyBands();
  if (bands.isLoading) return <Loading />;
  const mine = bands.data ?? [];
  if (mine.length === 0) return <Navigate to="/new-band" replace />;
  const last = lastBand();
  const band = mine.find((b) => b.slug === last) ?? mine[0];
  return <Navigate to={bandAddress(band.slug)} replace />;
}
