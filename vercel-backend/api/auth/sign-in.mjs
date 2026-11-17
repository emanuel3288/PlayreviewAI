import { setCors } from '../../lib/reviews.mjs';

export default function handler(_request, response) {
  setCors(response);
  return response.status(410).json({ detail: 'El acceso ahora se realiza con Firebase Authentication desde la aplicación.' });
}
