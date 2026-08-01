/**
 * Store-related types for the user-facing H5 app.
 * Backend source: com.petcare.store.dto.StoreResponse
 */

/** Store as returned by public store list API */
export interface StoreItem {
  id: string
  name: string
  address: string | null
  phone: string | null
  businessHours: string | null
  description: string | null
  status: string | null
}
