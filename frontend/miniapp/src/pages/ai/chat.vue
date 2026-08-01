<template>
  <view class="pc-page ai-chat">
    <!-- Conversation type switcher + new conversation button -->
    <view class="ai-chat__tabs">
      <view
        class="ai-chat__tab"
        :class="{ 'ai-chat__tab--active': pendingType === 'CUSTOMER_SERVICE' }"
        @tap="switchType('CUSTOMER_SERVICE')"
      >
        <text>智能客服</text>
      </view>
      <view
        class="ai-chat__tab"
        :class="{ 'ai-chat__tab--active': pendingType === 'PET_CHAT' }"
        @tap="switchType('PET_CHAT')"
      >
        <text>宠物闲聊</text>
      </view>
      <view
        v-if="isLoggedIn && messages.length > 0"
        class="ai-chat__new-btn"
        @tap="startNewConversation"
      >
        <text class="ai-chat__new-btn-text">✨ 新对话</text>
      </view>
    </view>

    <!-- Login gate: shown when user is not authenticated.
         Input bar stays below so users always see how to proceed. -->
    <view v-if="!isLoggedIn" class="ai-chat__login-gate">
      <text class="ai-chat__login-icon">🔒</text>
      <text class="ai-chat__login-title">请先登录</text>
      <text class="ai-chat__login-desc">登录后即可与 AI 助手对话</text>
      <view class="ai-chat__login-btn" @tap="goLogin">
        <text class="ai-chat__login-btn-text">去登录</text>
      </view>
    </view>

    <!-- Messages area: only the list is gated by PcStatePanel.
         Input bar is ALWAYS rendered (outside the panel) so it never disappears. -->
    <view v-else class="ai-chat__body">
      <scroll-view
        class="ai-chat__messages"
        scroll-y
        :style="{ height: scrollHeight + 'px' }"
        :scroll-into-view="lastMessageAnchor"
        :scroll-with-animation="true"
      >
        <!-- Empty hint when no messages yet -->
        <view v-if="messages.length === 0 && pageStatus !== 'error'" class="ai-chat__hint">
          <text class="ai-chat__hint-text">{{ inputHint }}</text>
        </view>

        <!-- Error banner (non-blocking: input still usable) -->
        <view v-if="pageStatus === 'error'" class="ai-chat__error">
          <text class="ai-chat__error-text">{{ errorMessage }}</text>
          <text class="ai-chat__error-retry" @tap="restoreOrCreate">重试</text>
        </view>

        <view
          v-for="msg in messages"
          :key="msg.id"
          :id="`msg-${msg.id}`"
          class="ai-chat__bubble-row"
          :class="{ 'ai-chat__bubble-row--user': msg.role === 'user' }"
        >
          <view class="ai-chat__avatar">
            <text class="ai-chat__avatar-text">{{ msg.role === 'user' ? '我' : 'AI' }}</text>
          </view>
          <view class="ai-chat__bubble" :class="msg.role === 'user' ? 'ai-chat__bubble--user' : 'ai-chat__bubble--ai'">
            <!-- User input stays plain text (never parsed as Markdown, prevents injection).
                 AI output is rendered as sanitized Markdown HTML for proper formatting. -->
            <text v-if="msg.role === 'user'" class="ai-chat__bubble-text">{{ msg.content }}</text>
            <!-- H5 renders Markdown via v-html (sanitized HTML string). -->
            <!-- #ifdef H5 -->
            <view v-else class="ai-chat__bubble-md" v-html="renderMarkdown(msg.content)" />
            <!-- #endif -->
            <!-- MP-WEIXIN: v-html is unsupported; rich-text accepts an HTML string as nodes.
                 We reuse the same sanitized Markdown output. rich-text has limited tag
                 support, so non-listed tags are dropped gracefully (acceptable for chat). -->
            <!-- #ifdef MP-WEIXIN -->
            <rich-text v-else class="ai-chat__bubble-md" :nodes="renderMarkdown(msg.content)" />
            <!-- #endif -->
          </view>
        </view>

        <view v-if="sending" class="ai-chat__bubble-row">
          <view class="ai-chat__avatar">
            <text class="ai-chat__avatar-text">AI</text>
          </view>
          <view class="ai-chat__bubble ai-chat__bubble--ai">
            <text class="ai-chat__bubble-text ai-chat__bubble-text--muted">正在思考...</text>
          </view>
        </view>
      </scroll-view>
    </view>

    <!-- Input bar: ALWAYS visible (login state disables it but keeps it shown). -->
    <view class="ai-chat__input-bar" :class="{ 'ai-chat__input-bar--disabled': !isLoggedIn }">
      <input
        class="ai-chat__input"
        type="text"
        v-model="draft"
        :placeholder="inputPlaceholder"
        confirm-type="send"
        :disabled="!isLoggedIn || sending"
        @confirm="handleSend"
      />
      <view
        class="ai-chat__send-btn"
        :class="{ 'ai-chat__send-btn--disabled': !canSend }"
        @tap="handleSend"
      >
        <text class="ai-chat__send-btn-text">{{ sending ? '...' : '发送' }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useUserStore } from '@/store/user'
import { createConversation, listMessages, listMyConversations, sendMessage } from '@/api/ai'
import { renderMarkdown } from '@/utils/markdown'
import type { AiConversationType, AiMessage } from '@/types/ai'

const userStore = useUserStore()
const isLoggedIn = computed(() => userStore.isLoggedIn)

const pendingType = ref<AiConversationType>('CUSTOMER_SERVICE')
const conversationId = ref<string>('')
const messages = ref<AiMessage[]>([])
const draft = ref('')
const sending = ref(false)
/** Only 'loading' | 'empty' | 'success' | 'error' — used for the messages list, not the whole page. */
const pageStatus = ref<'loading' | 'empty' | 'success' | 'error'>('empty')
const errorMessage = ref('操作失败，请稍后重试')
const lastMessageAnchor = ref('')

/**
 * Explicit pixel height for the messages scroll-view.
 * uni-app H5's scroll-view does NOT honor flex:1 reliably — its inner content overflows the
 * container and slides under the input bar. We compute a fixed height from window.innerHeight
 * minus the tab bar and input bar heights, and recompute on resize.
 * Fallback constants cover the case where the DOM measurements aren't ready yet.
 *
 * MP-WEIXIN note: the mini-program has no `document`/`window`, and its scroll-view does not
 * suffer the H5 overflow bug, so a pure constant-based calculation is sufficient there.
 */
const TABS_HEIGHT_PX = 48
const INPUT_BAR_HEIGHT_PX = 76
const scrollHeight = ref(0)

function recomputeScrollHeight() {
  try {
    const winH = uni.getSystemInfoSync().windowHeight

    // #ifdef H5
    // On H5, prefer measuring real element heights if available, fall back to constants.
    const tabs = document.querySelector('.ai-chat__tabs') as HTMLElement | null
    const input = document.querySelector('.ai-chat__input-bar') as HTMLElement | null
    const tabsH = tabs ? tabs.offsetHeight : TABS_HEIGHT_PX
    const inputH = input ? input.offsetHeight : INPUT_BAR_HEIGHT_PX
    scrollHeight.value = Math.max(120, winH - tabsH - inputH)
    // #endif

    // #ifdef MP-WEIXIN
    // No DOM in the mini-program; use the preset constants. The input bar padding
    // (safe-area bottom inset) is already reflected in INPUT_BAR_HEIGHT_PX.
    scrollHeight.value = Math.max(120, winH - TABS_HEIGHT_PX - INPUT_BAR_HEIGHT_PX)
    // #endif
  } catch {
    scrollHeight.value = 0
  }
}

const inputPlaceholder = computed(() => {
  if (!isLoggedIn.value) return '请先登录'
  return pendingType.value === 'CUSTOMER_SERVICE'
    ? '问问营业时间、服务价格...'
    : '和 AI 聊聊你家宠物'
})

const inputHint = computed(() =>
  pendingType.value === 'CUSTOMER_SERVICE'
    ? '有什么可以帮你？比如营业时间、服务价格、上门范围'
    : '和 AI 聊聊你家宠物的日常吧',
)

const canSend = computed(
  () => isLoggedIn.value && draft.value.trim().length > 0 && !sending.value,
)

function goLogin() {
  uni.navigateTo({ url: '/pages/auth/login' })
}

/**
 * Entry-point: restore the most recent conversation of the current type
 * instead of creating a new one every visit. This is what makes history persist
 * across page exits. Only creates a new conversation lazily on first send.
 */
async function restoreOrCreate() {
  if (!isLoggedIn.value) return
  pageStatus.value = 'loading'
  errorMessage.value = ''

  const recent = await findRecentConversation(pendingType.value)
  if (recent) {
    conversationId.value = recent.id
    await loadHistory()
    return
  }
  // No prior conversation — stay empty until the user sends the first message.
  conversationId.value = ''
  messages.value = []
  pageStatus.value = 'empty'
}

/** Finds the most recent conversation of the given type for the current user. */
async function findRecentConversation(type: AiConversationType) {
  try {
    const res = await listMyConversations({ page: 1, size: 20 })
    if (!res.success || !res.data) return null
    const matched = res.data.items.find((c) => c.conversationType === type)
    return matched || null
  } catch {
    return null
  }
}

/** Loads all messages for the current conversation (server is oldest-first). */
async function loadHistory() {
  if (!conversationId.value) return
  try {
    const res = await listMessages(conversationId.value, { page: 1, size: 100 })
    if (res.success && res.data) {
      messages.value = res.data.items
      pageStatus.value = messages.value.length === 0 ? 'empty' : 'success'
      if (messages.value.length > 0) scrollToEnd()
    } else {
      pageStatus.value = 'error'
      errorMessage.value = '历史消息加载失败'
    }
  } catch {
    pageStatus.value = 'error'
    errorMessage.value = '历史消息加载失败'
  }
}

/**
 * Explicit "start a new conversation" action (button in the header).
 * Lazily clears the local view without creating an empty conversation server-side;
 * the new conversation is created on the next send (see handleSend).
 */
function startNewConversation() {
  if (!isLoggedIn.value || sending.value) return
  conversationId.value = ''
  messages.value = []
  draft.value = ''
  errorMessage.value = ''
  pageStatus.value = 'empty'
}

async function switchType(type: AiConversationType) {
  if (type === pendingType.value) return
  if (!isLoggedIn.value) return
  pendingType.value = type
  await restoreOrCreate()
}

async function handleSend() {
  if (!canSend.value) return
  const content = draft.value.trim()
  draft.value = ''

  // Lazy-create a conversation on first send if none is active.
  if (!conversationId.value) {
    const created = await createConversation(pendingType.value)
    if (!created.success || !created.data) {
      errorMessage.value = '创建会话失败，请稍后重试'
      pageStatus.value = 'error'
      draft.value = content
      return
    }
    conversationId.value = created.data.id
  }

  const tempId = `local-${Date.now()}`
  messages.value.push({
    id: tempId,
    conversationId: conversationId.value,
    role: 'user',
    content,
  })
  pageStatus.value = 'success'
  scrollToEnd()
  errorMessage.value = ''

  sending.value = true
  try {
    const res = await sendMessage(conversationId.value, content)
    if (res.success && res.data) {
      messages.value.push(res.data)
    } else if (!res.success) {
      // Error already toasted by request wrapper; show inline banner too.
      errorMessage.value = '发送失败，请稍后重试'
      pageStatus.value = 'error'
    }
  } finally {
    sending.value = false
    scrollToEnd()
  }
}

function scrollToEnd() {
  const last = messages.value[messages.value.length - 1]
  if (!last) return
  // Two-tick so scroll-into-view re-triggers even if the anchor id is unchanged.
  nextTickSafe(() => {
    lastMessageAnchor.value = ''
    nextTickSafe(() => {
      lastMessageAnchor.value = `msg-${last.id}`
    })
  })
}

function nextTickSafe(cb: () => void) {
  // uni-app H5 supports Vue nextTick via a microtask; use setTimeout(0) as a portable fallback.
  setTimeout(cb, 0)
}

onLoad(async (query) => {
  if (query && query.type === 'PET_CHAT') {
    pendingType.value = 'PET_CHAT'
  }
  // Compute scroll height after first render so element measurements are available.
  nextTickSafe(() => recomputeScrollHeight())
  if (isLoggedIn.value) {
    await restoreOrCreate()
  }
})

// Recompute on resize (orientation change, browser chrome show/hide on mobile).
// H5-only: the mini-program has no resize event for this page, and its window size
// is queried fresh on each recomputeScrollHeight() call.
// #ifdef H5
if (typeof window !== 'undefined') {
  window.addEventListener('resize', recomputeScrollHeight)
}
// #endif
</script>

<style lang="scss" scoped>
.ai-chat {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #faf8f3;
}

.ai-chat__tabs {
  display: flex;
  align-items: center;
  background: #fff;
  border-bottom: 1rpx solid #eef0ed;
  flex-shrink: 0;
  padding-right: 16rpx;
}

.ai-chat__tab {
  flex: 1;
  padding: 24rpx 0;
  text-align: center;
  font-size: 28rpx;
  color: #71817d;
  position: relative;
}

.ai-chat__new-btn {
  flex-shrink: 0;
  padding: 12rpx 20rpx;
  background: #dff2ed;
  border-radius: 24rpx;
}

.ai-chat__new-btn-text {
  font-size: 24rpx;
  color: #0c4d48;
  white-space: nowrap;
}

.ai-chat__tab--active {
  color: #0c4d48;
  font-weight: 600;
}

.ai-chat__tab--active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 0;
  transform: translateX(-50%);
  width: 48rpx;
  height: 4rpx;
  background: #11796f;
  border-radius: 2rpx;
}

/* Login gate */
.ai-chat__login-gate {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48rpx;
  gap: 16rpx;
}

.ai-chat__login-icon {
  font-size: 80rpx;
}

.ai-chat__login-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #0c4d48;
}

.ai-chat__login-desc {
  font-size: 26rpx;
  color: #71817d;
}

.ai-chat__login-btn {
  margin-top: 16rpx;
  padding: 20rpx 64rpx;
  background: #11796f;
  border-radius: 40rpx;
}

.ai-chat__login-btn-text {
  color: #fff;
  font-size: 28rpx;
  font-weight: 500;
}

/* Messages body: takes remaining space; input bar sits below it. */
.ai-chat__body {
  /* The scroll-view inside has an explicit pixel height (see scrollHeight in script),
     so the body does not need flex sizing. Avoiding flex here prevents the uni-app
     scroll-view overflow bug where long content slides under the input bar. */
  min-height: 0;
}

.ai-chat__messages {
  /* Height comes from the inline :style binding (scrollHeight px).
     Keep box-sizing so padding doesn't inflate the rendered height. */
  box-sizing: border-box;
  padding: 24rpx;
}

.ai-chat__hint {
  padding: 120rpx 32rpx;
  text-align: center;
}

.ai-chat__hint-text {
  font-size: 26rpx;
  color: #71817d;
}

.ai-chat__error {
  margin: 0 24rpx 16rpx;
  padding: 20rpx 24rpx;
  background: #fff4f0;
  border: 1rpx solid #ffd9c4;
  border-radius: 12rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.ai-chat__error-text {
  font-size: 26rpx;
  color: #c0392b;
}

.ai-chat__error-retry {
  font-size: 26rpx;
  color: #11796f;
  font-weight: 600;
}

.ai-chat__bubble-row {
  display: flex;
  margin-bottom: 24rpx;
  align-items: flex-start;
}

.ai-chat__bubble-row--user {
  flex-direction: row-reverse;
}

.ai-chat__avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: #dff2ed;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.ai-chat__avatar-text {
  font-size: 24rpx;
  color: #0c4d48;
  font-weight: 600;
}

.ai-chat__bubble {
  max-width: 70%;
  padding: 20rpx 24rpx;
  border-radius: 20rpx;
  margin: 0 16rpx;
  word-break: break-word;
}

.ai-chat__bubble--ai {
  background: #fff;
  border: 1rpx solid #eef0ed;
  border-top-left-radius: 4rpx;
}

.ai-chat__bubble--user {
  background: #11796f;
  border-top-right-radius: 4rpx;
}

.ai-chat__bubble-text {
  font-size: 28rpx;
  color: #0c4d48;
  line-height: 1.5;
  white-space: pre-wrap;
}

/* Rendered Markdown content inside AI bubbles.
   Uses deep selector so scoped styles apply to v-html injected nodes. */
.ai-chat__bubble-md {
  font-size: 28rpx;
  color: #0c4d48;
  line-height: 1.6;
}
.ai-chat__bubble-md :deep(p) {
  margin: 0 0 12rpx;
}
.ai-chat__bubble-md :deep(p:last-child) {
  margin-bottom: 0;
}
.ai-chat__bubble-md :deep(strong) {
  font-weight: 600;
  color: #0c4d48;
}
.ai-chat__bubble-md :deep(ul),
.ai-chat__bubble-md :deep(ol) {
  margin: 8rpx 0 12rpx;
  padding-left: 36rpx;
}
.ai-chat__bubble-md :deep(li) {
  margin: 4rpx 0;
}
.ai-chat__bubble-md :deep(h1),
.ai-chat__bubble-md :deep(h2),
.ai-chat__bubble-md :deep(h3),
.ai-chat__bubble-md :deep(h4) {
  font-size: 30rpx;
  font-weight: 600;
  margin: 16rpx 0 8rpx;
  color: #0c4d48;
}
.ai-chat__bubble-md :deep(code) {
  background: #f5f7f5;
  padding: 2rpx 8rpx;
  border-radius: 6rpx;
  font-size: 26rpx;
}
.ai-chat__bubble-md :deep(pre) {
  background: #f5f7f5;
  padding: 16rpx;
  border-radius: 8rpx;
  overflow-x: auto;
  margin: 8rpx 0;
}
.ai-chat__bubble-md :deep(pre code) {
  background: transparent;
  padding: 0;
}

.ai-chat__bubble--user .ai-chat__bubble-text {
  color: #fff;
}

.ai-chat__bubble-text--muted {
  color: #71817d;
}

/* Input bar: always visible. */
.ai-chat__input-bar {
  display: flex;
  align-items: center;
  padding: 16rpx 24rpx 32rpx;
  background: #fff;
  border-top: 1rpx solid #eef0ed;
  flex-shrink: 0;
}

.ai-chat__input {
  flex: 1;
  height: 72rpx;
  padding: 0 24rpx;
  background: #f5f7f5;
  border-radius: 36rpx;
  font-size: 28rpx;
  color: #0c4d48;
}

.ai-chat__send-btn {
  margin-left: 16rpx;
  height: 72rpx;
  padding: 0 32rpx;
  background: #11796f;
  border-radius: 36rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ai-chat__send-btn--disabled {
  background: #c5d4cf;
}

.ai-chat__send-btn-text {
  font-size: 28rpx;
  color: #fff;
  font-weight: 500;
}
</style>
