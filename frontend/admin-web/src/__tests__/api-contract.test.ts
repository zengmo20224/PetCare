/**
 * API Contract Regression Tests (10F-R2B)
 *
 * These tests verify that frontend API definitions match the real backend.
 * Focus: no fake endpoints, correct DTO shapes, correct method/path,
 * required request fields, and nullable response fields.
 *
 * Backend controllers and request/response DTOs are the source of truth.
 */

import { describe, it, expect, vi } from 'vitest'

// Mock the request module so API functions can be tested without network
vi.mock('../utils/request', () => ({
  default: {
    get: vi.fn(() => Promise.resolve({ data: {} })),
    post: vi.fn(() => Promise.resolve({ data: {} })),
    put: vi.fn(() => Promise.resolve({ data: {} })),
    patch: vi.fn(() => Promise.resolve({ data: {} })),
    delete: vi.fn(() => Promise.resolve({ data: {} })),
  },
}))

// ─── Staff API Contract ───

describe('Staff API contract', () => {
  it('must export getStaffSkills (backend GET endpoint added in efd8632)', async () => {
    const staffModule = await import('../api/staff')
    expect(staffModule).toHaveProperty('getStaffSkills')
    expect(typeof staffModule.getStaffSkills).toBe('function')
  })

  it('getStaffSkills calls GET /v1/admin/staff/{id}/skills', async () => {
    const request = (await import('../utils/request')).default
    const { getStaffSkills } = await import('../api/staff')
    await getStaffSkills(42)
    expect(request.get).toHaveBeenCalledWith('/v1/admin/staff/42/skills')
  })

  it('must still export updateStaffSkills (real PUT endpoint)', async () => {
    const staffModule = await import('../api/staff')
    expect(staffModule).toHaveProperty('updateStaffSkills')
    expect(typeof staffModule.updateStaffSkills).toBe('function')
  })

  it('updateStaffSkills calls PUT /v1/admin/staff/{id}/skills', async () => {
    const request = (await import('../utils/request')).default
    const { updateStaffSkills } = await import('../api/staff')
    await updateStaffSkills(42, { serviceCategoryIds: [1, 2] })
    expect(request.put).toHaveBeenCalledWith(
      '/v1/admin/staff/42/skills',
      { serviceCategoryIds: [1, 2] },
    )
  })
})

// ─── Service Item Query Contract ───

describe('ServiceItem query contract', () => {
  it('ServiceItemQueryParams must NOT contain name field', async () => {
    const { getServiceItems } = await import('../api/service')
    const request = (await import('../utils/request')).default

    // Should only pass page, size, status — not name
    await getServiceItems({ page: 1, size: 20, status: 'ON_SALE' })
    expect(request.get).toHaveBeenCalledWith(
      '/v1/admin/service-items',
      { params: { page: 1, size: 20, status: 'ON_SALE' } },
    )
  })
})

// ─── Store Update Required Fields Contract ───

describe('StoreUpdateParams contract', () => {
  it('storeName and status are required (not optional)', async () => {
    // This is a compile-time contract — we verify the type exists
    // and the function passes the data through correctly
    const { updateStoreInfo } = await import('../api/store')
    const request = (await import('../utils/request')).default

    // storeName and status must be present (backend @NotBlank)
    await updateStoreInfo({
      storeName: 'Test Store',
      status: 'OPEN',
    })
    expect(request.patch).toHaveBeenCalled()
  })
})

// ─── Community PostReport Contract ───

describe('Community PostReport response contract', () => {
  it('PostReport type has reasonType (matches backend entity)', () => {
    // We verify by constructing a valid PostReport object
    // TypeScript enforces the shape at compile time
    const report: import('../api/community').PostReport = {
      id: 1,
      postId: 2,
      reporterId: 3,
      reasonType: 'SPAM',
      reason: 'spam content',
      status: 'PENDING',
      handleResult: null,
      handlerId: null,
      handleTime: null,
      createTime: '2026-01-01T00:00:00',
    }
    expect(report.reasonType).toBe('SPAM')
  })

  it('PostReport type has handlerId (matches backend entity)', () => {
    const report: import('../api/community').PostReport = {
      id: 1,
      postId: 2,
      reporterId: 3,
      reasonType: null,
      reason: '',
      status: 'PENDING',
      handleResult: null,
      handlerId: 100,
      handleTime: null,
      createTime: '2026-01-01T00:00:00',
    }
    expect(report.handlerId).toBe(100)
  })
})

describe('Community report handle request contract', () => {
  it('handleReport request allows handleRemark (backend AdminReportHandleRequest)', async () => {
    const { handleReport } = await import('../api/community')
    const request = (await import('../utils/request')).default

    await handleReport(1, {
      handleResult: 'PROCESSED',
      hidePost: true,
      handleRemark: 'spam removed',
    })
    expect(request.post).toHaveBeenCalledWith(
      '/v1/admin/community/reports/1/handle',
      expect.objectContaining({ handleRemark: 'spam removed' }),
    )
  })
})

// ─── Booking Reassign Contract ───

describe('Booking reassign contract', () => {
  it('reassignBooking calls POST /v1/admin/bookings/{id}/reassign', async () => {
    const { reassignBooking } = await import('../api/booking')
    const request = (await import('../utils/request')).default

    await reassignBooking(10, { newStaffId: 20 })
    expect(request.post).toHaveBeenCalledWith(
      '/v1/admin/bookings/10/reassign',
      { newStaffId: 20 },
    )
  })
})

// ─── Product Order Out-of-Stock Contract ───

describe('Product order out-of-stock contract', () => {
  it('outOfStockProductOrder calls POST /v1/admin/product-orders/{id}/out-of-stock', async () => {
    const { outOfStockProductOrder } = await import('../api/product-order')
    const request = (await import('../utils/request')).default

    await outOfStockProductOrder(5)
    expect(request.post).toHaveBeenCalledWith(
      '/v1/admin/product-orders/5/out-of-stock',
      undefined,
    )
  })

  it('outOfStockProductOrder passes optional reason', async () => {
    const { outOfStockProductOrder } = await import('../api/product-order')
    const request = (await import('../utils/request')).default

    await outOfStockProductOrder(5, { reason: 'supplier issue' })
    expect(request.post).toHaveBeenCalledWith(
      '/v1/admin/product-orders/5/out-of-stock',
      { reason: 'supplier issue' },
    )
  })
})

// ─── DTO Shape Contracts (compile-time enforcement) ───
// These tests verify key type shapes by constructing objects.
// If the type definition changes, TypeScript compilation will fail.

describe('Booking response DTO shape', () => {
  it('Booking allows nullable petId, staffId, remark, merchantRemark', () => {
    const booking: import('../api/booking').Booking = {
      id: 1,
      bookingNo: 'BN001',
      userId: 1,
      petId: null,
      storeId: 1,
      serviceItemId: 1,
      staffId: null,
      serviceMode: 'STORE',
      bookingDate: '2026-01-01',
      startTime: '09:00',
      endTime: '10:00',
      addressId: null,
      distanceKm: null,
      contactName: 'Test',
      contactPhone: '123',
      price: 100,
      paymentMethod: 'OFFLINE',
      paymentStatus: 'UNPAID',
      status: 'PENDING_CONFIRM',
      remark: null,
      merchantRemark: null,
      createTime: '2026-01-01T00:00:00',
    }
    expect(booking.petId).toBeNull()
    expect(booking.staffId).toBeNull()
    expect(booking.remark).toBeNull()
    expect(booking.merchantRemark).toBeNull()
  })
})

describe('StoreInfo response DTO shape', () => {
  it('StoreInfo allows nullable phone, address, longitude, latitude, businessHours, description', () => {
    const store: import('../api/store').StoreInfo = {
      id: 1,
      storeName: 'Test',
      phone: null,
      address: null,
      longitude: null,
      latitude: null,
      businessHours: null,
      status: 'OPEN',
      description: null,
    }
    expect(store.phone).toBeNull()
    expect(store.address).toBeNull()
    expect(store.description).toBeNull()
  })
})

describe('ServiceItem response DTO shape', () => {
  it('ServiceItem allows nullable petType, petSize, description, coverUrl, sort', () => {
    const item: import('../api/service').ServiceItem = {
      id: 1,
      categoryId: 1,
      name: 'Test',
      serviceMode: 'STORE',
      price: 100,
      durationMinutes: 60,
      petType: null,
      petSize: null,
      needAddress: false,
      needPet: false,
      description: null,
      coverUrl: null,
      imageUrls: [],
      status: 'ACTIVE',
      sort: null,
    }
    expect(item.petType).toBeNull()
    expect(item.petSize).toBeNull()
    expect(item.description).toBeNull()
    expect(item.coverUrl).toBeNull()
    expect(item.imageUrls).toEqual([])
    expect(item.sort).toBeNull()
  })
})

describe('Product response DTO shape', () => {
  it('Product allows nullable coverUrl, description, sort and detail imageUrls', () => {
    const product: import('../api/product').Product = {
      id: 1,
      categoryId: 1,
      name: 'Test',
      coverUrl: null,
      price: 50,
      stock: 10,
      salesCount: 0,
      description: null,
      pickupOnly: true,
      status: 'ON_SALE',
      sort: null,
      imageUrls: [],
      detailImageUrls: [],
    }
    expect(product.coverUrl).toBeNull()
    expect(product.description).toBeNull()
    expect(product.sort).toBeNull()
    expect(product.imageUrls).toEqual([])
    expect(product.detailImageUrls).toEqual([])
  })
})

describe('Product carousel admin API contract', () => {
  it('listProductCarouselImages calls GET /v1/admin/product-carousel-images', async () => {
    const { listProductCarouselImages } = await import('../api/product-carousel')
    const request = (await import('../utils/request')).default

    await listProductCarouselImages()
    expect(request.get).toHaveBeenCalledWith('/v1/admin/product-carousel-images')
  })

  it('replaceProductCarouselImages calls PUT /v1/admin/product-carousel-images', async () => {
    const { replaceProductCarouselImages } = await import('../api/product-carousel')
    const request = (await import('../utils/request')).default

    const payload = {
      images: [{
        title: '新品推荐',
        imageUrl: 'https://example.com/banner.jpg',
        linkType: 'PRODUCT',
        linkTargetId: 1,
        status: 'ACTIVE',
        sort: 1,
      }],
    }
    await replaceProductCarouselImages(payload)
    expect(request.put).toHaveBeenCalledWith('/v1/admin/product-carousel-images', payload)
  })

  it('ProductCarouselImage allows nullable title, linkType and linkTargetId', () => {
    const image: import('../api/product-carousel').ProductCarouselImage = {
      id: 1,
      title: null,
      imageUrl: 'https://example.com/banner.jpg',
      linkType: null,
      linkTargetId: null,
      status: 'ACTIVE',
      sort: 1,
    }
    expect(image.linkTargetId).toBeNull()
  })
})

describe('StaffMember response DTO shape', () => {
  it('StaffMember allows nullable phone, avatarUrl, description', () => {
    const staff: import('../api/staff').StaffMember = {
      id: 1,
      storeId: 1,
      name: 'Test',
      phone: null,
      avatarUrl: null,
      role: 'GROOMER',
      status: 'ACTIVE',
      description: null,
    }
    expect(staff.phone).toBeNull()
    expect(staff.avatarUrl).toBeNull()
    expect(staff.description).toBeNull()
  })
})

describe('StaffSchedule response DTO shape', () => {
  it('StaffSchedule allows nullable remark', () => {
    const schedule: import('../api/staff').StaffSchedule = {
      id: 1,
      staffId: 1,
      storeId: 1,
      workDate: '2026-01-01',
      startTime: '09:00',
      endTime: '17:00',
      status: 'AVAILABLE',
      remark: null,
    }
    expect(schedule.remark).toBeNull()
  })
})

describe('SensitiveWord response DTO shape', () => {
  it('SensitiveWord allows nullable category', () => {
    const word: import('../api/moderation').SensitiveWord = {
      id: 1,
      word: 'test',
      category: null,
      level: 1,
      status: 'ACTIVE',
      createTime: '2026-01-01T00:00:00',
    }
    expect(word.category).toBeNull()
  })
})

describe('Auth response DTO shape', () => {
  it('AdminLoginResult.admin allows nullable nickname and role', () => {
    const loginResult: import('../api/auth').AdminLoginResult = {
      tokenType: 'Bearer',
      accessToken: 'token',
      expiresInSeconds: 3600,
      admin: {
        id: 1,
        username: 'admin',
        nickname: null,
        role: null,
      },
    }
    expect(loginResult.admin.nickname).toBeNull()
    expect(loginResult.admin.role).toBeNull()
  })

  it('AdminUserInfo allows nullable nickname, role, permissions', () => {
    const user: import('../api/auth').AdminUserInfo = {
      id: 1,
      username: 'admin',
      nickname: null,
      role: null,
      permissions: null,
    }
    expect(user.nickname).toBeNull()
    expect(user.role).toBeNull()
    expect(user.permissions).toBeNull()
  })
})
