/** A band's address from its name: lower case, no accents, dashes between words (as the server accepts it). */
export function slugOf(name: string): string {
  return name.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()
    .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 64).replace(/-+$/g, '');
}

/** Whether the server takes it as a band's address: 2–64 lower case letters, digits and dashes, no dash at either end. */
export function validSlug(slug: string): boolean {
  return /^[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])$/.test(slug);
}
