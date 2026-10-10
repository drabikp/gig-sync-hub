import { useEffect } from 'react';
import { Navigate, useParams } from 'react-router';
import { Loading } from '../components/Loading';
import { useMyBands } from '../api/hooks';
import { AppLayout } from '../layout/AppLayout';
import { BandProvider, rememberBand } from './band';

/** A band's pages (/b/<slug>/…): the band the address names, if it is one of the user's — else the start. */
export function BandPages() {
  const { slug } = useParams();
  const bands = useMyBands();
  const band = bands.data?.find((b) => b.slug === slug);
  useEffect(() => {
    if (band) rememberBand(band.slug);
  }, [band]);
  if (bands.isLoading) return <Loading />;
  if (!band) return <Navigate to="/" replace />;
  return (
    <BandProvider band={band}>
      <AppLayout key={band.id} />
    </BandProvider>
  );
}
