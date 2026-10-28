/**
 * CampusVerse Lite - Frontend Application
 * Fully integrated Single Page Application (SPA) with Java Jakarta EE Backend.
 * Uses real database-backed state, CSRF protection, and secure textContent rendering.
 */

// Context and CSRF setup
const ctx = window.APP_CONTEXT || '';
function getCsrfToken() {
    return window.CSRF_TOKEN || document.querySelector('meta[name="csrf-token"]')?.content || '';
}

// Selector & Sanitization utilities
const $ = s => document.querySelector(s);
const $$ = s => document.querySelectorAll(s);
const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
}[c]));

function initials(nameStr) {
    if (!nameStr) return 'ST';
    const clean = nameStr.replace(/_/g, ' ').trim();
    const parts = clean.split(/\s+/);
    if (parts.length >= 2 && parts[0] && parts[1]) {
        return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return clean.substring(0, Math.min(2, clean.length)).toUpperCase();
}

// Form-encoded API helper with CSRF protection
async function apiPost(url, params = {}) {
    const token = getCsrfToken();
    const urlParams = new URLSearchParams();
    for (const [key, val] of Object.entries(params)) {
        if (Array.isArray(val)) {
            val.forEach(v => urlParams.append(key, v));
        } else if (val !== null && val !== undefined) {
            urlParams.append(key, val);
        }
    }
    urlParams.set('csrfToken', token);

    return fetch(url, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
            'Accept': 'application/json',
            'X-Requested-With': 'XMLHttpRequest',
            'X-CSRF-Token': token
        },
        body: urlParams.toString()
    });
}

// Global Application State (backed by server database)
const state = {
    profile: {
        id: (window.INITIAL_STUDENT && window.INITIAL_STUDENT.id) || null,
        name: (window.INITIAL_STUDENT && window.INITIAL_STUDENT.name) || 'Student',
        interests: []
    },
    allInterests: ['Java', 'DSA', 'UI/UX', 'React', 'Hackathons', 'Gaming', 'DBMS', 'Python'],
    studyRooms: [],
    ghostRooms: [],
    doubts: [],
    matches: [],
    messages: {}, // roomId -> array of message objects
    savedCircle: [] // UI preference: peer IDs saved locally
};

// Load saved UI preferences from localStorage
try {
    const pref = JSON.parse(localStorage.getItem('campusverse-saved-circle') || '[]');
    if (Array.isArray(pref)) state.savedCircle = pref;
} catch (e) {
    state.savedCircle = [];
}

function saveUiPref() {
    try {
        localStorage.setItem('campusverse-saved-circle', JSON.stringify(state.savedCircle));
    } catch (e) {}
}

// Navigation definitions
const navs = [
    ['home', '▦', 'Overview'],
    ['chat', '▤', 'Study Chat'],
    ['match', '✳', 'VibeMatch'],
    ['ghost', '♧', 'GhostRooms'],
    ['doubts', '◈', 'DoubtDrop']
];

const subjects = ['DSA', 'Java', 'DBMS', 'General'];

let current = 'home';
let activeRoomId = null;
let activeRoomName = 'Java';
let isGhostRoom = false;
let doubtFilter = 'All';
let doubtSearch = '';
let chatPollTimer = null;
let lastPolledMessageId = 0;

function toast(text) {
    const el = $('#toast');
    if (!el) return;
    el.textContent = text;
    el.classList.add('show');
    clearTimeout(window.tt);
    window.tt = setTimeout(() => el.classList.remove('show'), 3500);
}

function heading(title, sub, action = '') {
    return `
        <div class="page-heading">
            <div>
                <div class="eyebrow">A LITTLE COMMUNITY. A LOT OF POSSIBILITY.</div>
                <h1>${title}</h1>
                <p>${sub}</p>
            </div>
            ${action}
        </div>
    `;
}

// ----------------------------------------------------
// ROUTING & RENDERING
// ----------------------------------------------------
function navigate(page) {
    if (current === 'chat' && page !== 'chat') {
        stopChatPolling();
    }
    current = page;
    location.hash = page;
    try {
        localStorage.setItem('campusverse-pref-tab', page);
    } catch (e) {}
    render();
    window.scrollTo(0, 0);
}

function render() {
    // Navigation bar buttons
    const navEl = $('#nav');
    if (navEl) {
        navEl.innerHTML = navs.map(([key, icon, label]) => `
            <button onclick="navigate('${key}')" class="${current === key ? 'active' : ''}">
                <span class="symbol">${icon}</span>
                <span>${label}</span>
                ${key === 'ghost' ? '<span class="badge">LIVE</span>' : ''}
            </button>
        `).join('');
    }

    // Breadcrumb and headers
    const crumb = $('#crumb');
    if (crumb) {
        crumb.textContent = navs.find(x => x[0] === current)?.[2] ||
            (current === 'saved' ? 'Your activity' : 'Your profile');
    }

    const userNameEl = $('#user-name');
    if (userNameEl) userNameEl.textContent = state.profile.name;

    const currentInitials = initials(state.profile.name);
    const headerAvatar = $('.header-right .avatar') || $('.header-avatar');
    if (headerAvatar) headerAvatar.textContent = currentInitials;

    const profileAvatar = $('.profile-button .avatar');
    if (profileAvatar) profileAvatar.textContent = currentInitials;

    // View router
    const views = {
        home: renderHome,
        chat: renderChat,
        match: renderMatch,
        ghost: renderGhosts,
        doubts: renderDoubts,
        profile: renderProfile,
        saved: renderSaved
    };

    const mainEl = $('#main');
    if (mainEl) {
        mainEl.innerHTML = (views[current] || renderHome)();
    }

    // Post-render lifecycle hooks
    if (current === 'chat') {
        const msgsEl = $('#chat-messages');
        if (msgsEl) msgsEl.scrollTop = msgsEl.scrollHeight;
        startChatPolling();
    }
}

// ----------------------------------------------------
// VIEW: Home (Overview)
// ----------------------------------------------------
function renderHome() {
    const firstName = esc(state.profile.name.split(' ')[0]);
    const liveGhostCount = state.ghostRooms.filter(g => !g.expired && g.expires > Date.now()).length;
    const openDoubtsCount = state.doubts.filter(d => !d.resolved).length;
    const roomsCount = state.studyRooms.length || 4;
    const peersCount = state.matches.length;

    // Pick top open doubt for teaser
    const topDoubt = state.doubts.find(d => !d.resolved);

    return heading(
        `Hey ${firstName}, find your people <span style="color:#acb77a">✳</span>`,
        'A little help, a good conversation, and your next big idea. It all starts here.',
        `<span class="date">◷ &nbsp; YOUR CAMPUS, YOUR CORNER</span>`
    ) + `
        <section class="hero">
            <div class="hero-content">
                <span class="hero-label">LESS SCROLLING. MORE CONNECTING.</span>
                <h2>Your campus.<br>Your people. <em>Your verse.</em></h2>
                <p>From “I don’t get it” to “we’ve got this.”<br>Find a study circle that feels like you.</p>
                <button class="primary" onclick="navigate('match')">Find my people <span>↗</span></button>
            </div>
            <div class="hero-art" aria-hidden="true">
                <div class="orbit"></div>
                <span class="art-star">✳</span>
                <div class="art-card art-chat">☏</div>
                <div class="art-card art-ghost"><div class="ghost"></div></div>
                <span class="art-spark">✧</span>
                <span class="art-caption">a good kind of campus chaos.</span>
            </div>
        </section>

        <section class="stats">
            <div class="stat">
                <span class="stat-icon">♧</span>
                <div><b>${String(roomsCount).padStart(2, '0')}</b><small>Study rooms</small></div>
            </div>
            <div class="stat">
                <span class="stat-icon">✳</span>
                <div><b>${String(peersCount).padStart(2, '0')}</b><small>People to discover</small></div>
            </div>
            <div class="stat">
                <span class="stat-icon">◈</span>
                <div><b>${String(openDoubtsCount).padStart(2, '0')}</b><small>Open conversations</small></div>
            </div>
            <div class="stat">
                <span class="stat-icon">◷</span>
                <div><b>${String(liveGhostCount).padStart(2, '0')}</b><small>GhostRooms live</small></div>
            </div>
        </section>

        <div class="section-heading">
            <div>
                <h2>Make yourself at home</h2>
                <p>Four ways to make campus feel a little smaller.</p>
            </div>
            <span class="text-link">Pick your vibe ↘</span>
        </div>

        <section class="feature-grid">
            <button class="feature" onclick="navigate('chat')">
                <span class="feature-symbol">▤</span>
                <h3>Study Chat</h3>
                <p>Big questions. Small study circles.<br>Learn better, together.</p>
                <div class="feature-bottom">
                    <span>${roomsCount} subject rooms</span>
                    <span>↗</span>
                </div>
            </button>
            <button class="feature" onclick="navigate('match')">
                <span class="feature-symbol">✳</span>
                <h3>VibeMatch</h3>
                <p>Same interests. Fresh connections.<br>Meet your kind of people.</p>
                <div class="feature-bottom">
                    <span>Find your circle</span>
                    <span>↗</span>
                </div>
            </button>
            <button class="feature" onclick="navigate('ghost')">
                <span class="feature-symbol">♧</span>
                <span class="tiny-tag">EPHEMERAL</span>
                <h3>GhostRooms</h3>
                <p>Here for a good time, not a long time.<br>Rooms that disappear.</p>
                <div class="feature-bottom">
                    <span>Drop in. Talk. Vanish.</span>
                    <span>↗</span>
                </div>
            </button>
            <button class="feature" onclick="navigate('doubts')">
                <span class="feature-symbol">◈</span>
                <h3>DoubtDrop</h3>
                <p>No “silly” questions here.<br>Get unstuck with a little help.</p>
                <div class="feature-bottom">
                    <span>Ask away</span>
                    <span>↗</span>
                </div>
            </button>
        </section>

        <div class="lower-grid">
            <section>
                <div class="section-heading">
                    <h2>Find your study corner</h2>
                    <button class="text-link" onclick="navigate('chat')">View all rooms ↗</button>
                </div>
                <div class="panel">
                    ${(state.studyRooms.length ? state.studyRooms : [
                        { id: 1, name: 'Java' },
                        { id: 2, name: 'DSA' },
                        { id: 3, name: 'DBMS' }
                    ]).map(r => `
                        <div class="room-row">
                            <span class="room-icon">${r.name === 'Java' ? '☕' : r.name === 'DSA' ? '⌘' : '▥'}</span>
                            <div class="room-info">
                                <b>${esc(r.name)} Study Circle</b>
                                <p><i></i> Peer discussions and live learning</p>
                            </div>
                            <button class="room-action" onclick="openRoomById(${r.id}, '${esc(r.name)}', false)">Join ↗</button>
                        </div>
                    `).join('')}
                </div>
            </section>

            <section>
                <div class="section-heading">
                    <h2>A little help goes a long way</h2>
                    <button class="text-link" onclick="navigate('doubts')">DoubtDrop ↗</button>
                </div>
                <div class="panel">
                    ${topDoubt ? `
                        <div class="doubt-teaser">
                            <span class="chip">${esc(topDoubt.subject)}</span>
                            <span class="chip green">Open for ideas</span>
                            <h3>${esc(topDoubt.title)}</h3>
                            <div class="doubt-meta">
                                <span><span class="avatar pink" style="width:20px;height:20px;font-size:7px">${esc(initials(topDoubt.author))}</span> &nbsp; ${esc(topDoubt.author)}</span>
                                <button onclick="viewDoubt(${topDoubt.id})">${(topDoubt.replies || []).length} replies ↗</button>
                            </div>
                        </div>
                        <p class="encourage">Know the answer? <button onclick="viewDoubt(${topDoubt.id})">Be someone’s lightbulb moment ↗</button></p>
                    ` : `
                        <div class="empty" style="padding: 30px 10px;">
                            <p style="margin: 0 0 10px; font-weight: 600;">No open doubts right now.</p>
                            <small class="muted">Have an academic question? Be the first to ask in DoubtDrop.</small>
                            <div style="margin-top: 15px;"><button class="primary" onclick="askDoubt()">+ Drop a doubt</button></div>
                        </div>
                    `}
                </div>
            </section>
        </div>
    `;
}

// ----------------------------------------------------
// VIEW: Study Chat
// ----------------------------------------------------
function openRoomById(id, name, isGhost = false) {
    activeRoomId = Number(id);
    activeRoomName = name;
    isGhostRoom = isGhost;
    lastPolledMessageId = 0;
    navigate('chat');
}

function stopChatPolling() {
    if (chatPollTimer) {
        clearInterval(chatPollTimer);
        chatPollTimer = null;
    }
}

function startChatPolling() {
    stopChatPolling();
    // Immediate initial sync
    syncCurrentChat();
    // Poll roughly every 3 seconds while on chat screen
    chatPollTimer = setInterval(syncCurrentChat, 3000);
}

function renderChat() {
    // Select default room if none active
    if (!activeRoomId && state.studyRooms.length > 0) {
        activeRoomId = state.studyRooms[0].id;
        activeRoomName = state.studyRooms[0].name;
        isGhostRoom = false;
    }

    let ghost = isGhostRoom ? state.ghostRooms.find(g => Number(g.id) === activeRoomId) : null;
    let expired = ghost && (ghost.expired || (ghost.expires && ghost.expires <= Date.now()));
    const msgs = state.messages[activeRoomId] || [];

    return heading('A good place to get it.', 'Ask a question, share a thought, or just say hello.') + `
        <div class="info-banner">Live Chat · Connected to persistent H2 database. Polls for updates every 3 seconds with draft preservation and duplicate prevention.</div>
        <div id="chat-error-banner" style="display:none; padding:10px 14px; background:#fee2e2; color:#991b1b; border-radius:8px; margin-bottom:15px; font-size:12px;"></div>
        <div class="workspace">
            <aside class="panel room-list">
                ${state.studyRooms.map(r => `
                    <button class="${activeRoomId === r.id && !isGhostRoom ? 'active' : ''}" onclick="openRoomById(${r.id}, '${esc(r.name)}', false)">
                        # ${esc(r.name)}
                        <small>${r.name === 'General' ? 'Campus common room' : 'Your ' + esc(r.name) + ' study circle'}</small>
                    </button>
                `).join('')}

                ${state.ghostRooms.map(g => `
                    <button class="${activeRoomId === Number(g.id) && isGhostRoom ? 'active' : ''}" onclick="openRoomById(${g.id}, '${esc(g.title)}', true)">
                        ♧ ${esc(g.title)}
                        <small>${(g.expired || (g.expires && g.expires <= Date.now())) ? 'Expired GhostRoom' : 'Temporary Room'}</small>
                    </button>
                `).join('')}
            </aside>

            <section class="panel chat-panel">
                <div class="chat-top">
                    ${isGhostRoom ? '♧ ' + esc(activeRoomName) : '# ' + esc(activeRoomName) + ' Study Circle'}
                    <small>${expired ? 'This GhostRoom has expired. You can still read the conversation.' : 'A little curiosity goes a long way. Be kind, always.'}</small>
                </div>

                <div class="messages" id="chat-messages">
                    ${msgs.length === 0 ? `
                        <div class="empty" id="chat-empty-msg">
                            No messages yet in this study circle. Start the conversation!
                        </div>
                    ` : ''}
                </div>

                <form class="compose" onsubmit="sendMessage(event)">
                    <input name="message" id="chat-compose-input" maxlength="2000" aria-label="Message"
                           placeholder="${expired ? 'This GhostRoom is closed to new messages' : 'A question? A thought? Drop it here…'}"
                           required ${expired ? 'disabled' : ''} autocomplete="off">
                    <button class="primary" id="chat-send-btn" ${expired ? 'disabled' : ''}>Send ↗</button>
                </form>
            </section>
        </div>
    `;
}

function showChatError(text) {
    const el = $('#chat-error-banner');
    if (!el) return;
    el.textContent = text;
    el.style.display = 'block';
    setTimeout(() => { if (el) el.style.display = 'none'; }, 5000);
}

// Strictly safe textContent-based DOM rendering for chat messages
function appendMessageToDOM(container, m) {
    const emptyMsg = $('#chat-empty-msg');
    if (emptyMsg) emptyMsg.remove();

    const isMine = (m.senderNickname === state.profile.name) || (m.author === state.profile.name);
    const item = document.createElement('div');
    item.className = 'message' + (isMine ? ' mine' : '');
    item.dataset.id = m.id;

    const avatar = document.createElement('span');
    avatar.className = 'avatar ' + (isMine ? 'lime' : 'purple');
    avatar.textContent = initials(m.senderNickname || m.author || 'ST');

    const wrapper = document.createElement('div');

    const sender = document.createElement('b');
    sender.textContent = m.senderNickname || m.author || 'Student';

    const time = document.createElement('time');
    const timeText = m.createdAt ? m.createdAt.substring(11, 16) : (m.time || '');
    time.textContent = timeText;

    const bubble = document.createElement('div');
    bubble.className = 'bubble';
    bubble.textContent = m.content || m.text || ''; // Strict textContent! Eliminates script injection

    wrapper.appendChild(sender);
    wrapper.appendChild(time);
    wrapper.appendChild(bubble);

    item.appendChild(avatar);
    item.appendChild(wrapper);

    container.appendChild(item);
}

async function syncCurrentChat() {
    if (!activeRoomId || current !== 'chat') return;

    try {
        const res = await fetch(`${ctx}/api/messages?roomId=${activeRoomId}&afterId=${lastPolledMessageId}`);
        if (!res.ok) {
            if (res.status === 401) {
                // Session expired
                toast('Session expired. Please log in again.');
            }
            return;
        }

        const msgs = await res.json();
        if (Array.isArray(msgs) && msgs.length > 0) {
            const container = $('#chat-messages');
            state.messages[activeRoomId] ??= [];

            for (const m of msgs) {
                if (m.id > lastPolledMessageId) {
                    lastPolledMessageId = m.id;
                }

                // Check for duplicates
                const exists = state.messages[activeRoomId].some(local => local.id === m.id);
                if (!exists) {
                    state.messages[activeRoomId].push(m);
                    if (container && current === 'chat') {
                        appendMessageToDOM(container, m);
                    }
                }
            }

            if (container && current === 'chat') {
                container.scrollTop = container.scrollHeight;
            }
        }
    } catch (err) {
        // Polling failure tolerated silently
    }
}

async function sendMessage(e) {
    e.preventDefault();
    const form = e.target;
    const input = form.querySelector('input[name="message"]');
    const sendBtn = form.querySelector('button');
    const text = input ? input.value.trim() : '';
    if (!text || !activeRoomId) return;

    // Check client-side expiry for GhostRooms
    if (isGhostRoom) {
        const g = state.ghostRooms.find(x => Number(x.id) === activeRoomId);
        if (g && (g.expired || (g.expires && g.expires <= Date.now()))) {
            toast('This GhostRoom has expired.');
            showChatError('This room is closed to new messages.');
            render();
            return;
        }
    }

    if (sendBtn) sendBtn.disabled = true;

    try {
        const res = await apiPost(`${ctx}/messages`, {
            roomId: activeRoomId,
            content: text
        });

        if (res.ok) {
            // Clear message draft ONLY on confirmed success!
            input.value = '';
            // Immediately pull new message
            await syncCurrentChat();
        } else {
            const data = await res.json().catch(() => ({}));
            const errStr = data.error || 'Failed to send message. Your draft was preserved.';
            toast(errStr);
            showChatError(errStr);
        }
    } catch (err) {
        toast('Network connection failed. Your message draft was preserved.');
        showChatError('Network error. Your message draft was preserved.');
    } finally {
        if (sendBtn) sendBtn.disabled = false;
    }
}

// ----------------------------------------------------
// VIEW: VibeMatch
// ----------------------------------------------------
function renderMatch() {
    const chosen = state.profile.interests || [];
    const list = state.matches || [];

    return heading('Your people are out there.', 'Start with what you love. Find someone who gets it.') + `
        <div class="info-banner">VibeMatch computes Jaccard Similarity index (intersection ÷ union) across students stored in the database. Updating your interests recalculates recommendations immediately on the server.</div>

        <div class="section-heading">
            <h2>What’s your thing?</h2>
            <span class="muted">Select your technical interests. Recommendations update live.</span>
        </div>

        <div class="interest-picks">
            ${state.allInterests.map(i => `
                <button class="${chosen.includes(i) ? 'selected' : ''}" onclick="toggleInterest('${esc(i)}')">
                    ${chosen.includes(i) ? '✓ ' : '+ '}${esc(i)}
                </button>
            `).join('')}
        </div>

        <div class="card-grid">
            ${list.length ? list.map(p => `
                <article class="person">
                    <span class="match">${p.score}% match</span>
                    <span class="avatar ${p.score > 50 ? 'lime' : 'peach'}">${esc(p.initials || initials(p.name))}</span>
                    <h3>${esc(p.name)}</h3>
                    <span class="muted">${esc(p.year || 'CSE · 2nd year')}</span>
                    <p>${esc(p.bio || 'Building things. Learning together on CampusVerse.')}</p>
                    <div>
                        ${(p.interests || []).map(i => `
                            <span class="chip ${chosen.includes(i) ? 'green' : ''}">${esc(i)}</span>
                        `).join('')}
                    </div>
                    <button class="primary" onclick="toggleConnect('${esc(p.name)}')">
                        ${state.savedCircle.includes(p.name) ? '✓ Saved to your circle' : 'Add to my circle ↗'}
                    </button>
                </article>
            `).join('') : `
                <div class="empty" style="grid-column: 1 / -1;">
                    <p style="font-weight:600; margin-bottom: 8px;">No other student profiles found yet.</p>
                    <small class="muted">Invite classmates to sign into CampusVerse Lite with their nickname to see live match percentages!</small>
                </div>
            `}
        </div>
    `;
}

async function toggleInterest(interestName) {
    const arr = state.profile.interests || [];
    if (arr.includes(interestName)) {
        state.profile.interests = arr.filter(x => x !== interestName);
    } else {
        state.profile.interests = [...arr, interestName];
    }
    render();

    // Persist to backend and reload VibeMatch
    try {
        await apiPost(`${ctx}/student`, {
            action: 'saveProfile',
            nickname: state.profile.name,
            interests: state.profile.interests
        });

        // Refetch matches with updated interests
        await loadMatches();
        if (current === 'match') {
            render();
        }
    } catch (e) {
        toast('Could not save interest to server.');
    }
}

function toggleConnect(peerName) {
    if (state.savedCircle.includes(peerName)) {
        state.savedCircle = state.savedCircle.filter(x => x !== peerName);
        toast('Removed from your circle');
    } else {
        state.savedCircle.push(peerName);
        toast('Saved to your circle');
    }
    saveUiPref();
    render();
}

// ----------------------------------------------------
// VIEW: GhostRooms
// ----------------------------------------------------
function countdown(t) {
    let s = Math.max(0, Math.floor((t - Date.now()) / 1000));
    if (!s) return 'Room closed';
    const hrs = String(Math.floor(s / 3600)).padStart(2, '0');
    const mins = String(Math.floor(s / 60) % 60).padStart(2, '0');
    const secs = String(s % 60).padStart(2, '0');
    return `${hrs} : ${mins} : ${secs}`;
}

function renderGhosts() {
    const list = state.ghostRooms || [];

    return heading(
        'Good conversations. Limited editions.',
        'A room for right now. Revision, ideas, or whatever’s on your mind.',
        `<button class="primary" onclick="createGhost()">+ Create a room</button>`
    ) + `
        <div class="info-banner">GhostRooms automatically lock and reject new messages after their timer expires. Stored securely in H2 database.</div>
        <div class="card-grid">
            ${list.length ? list.map(g => {
                const isExp = g.expired || (g.expires && g.expires <= Date.now());
                return `
                    <article class="ghost-card">
                        <div class="ghost" aria-hidden="true"></div>
                        <h3>${esc(g.title)}</h3>
                        <p>${esc(g.topic || 'Live temporary study session')}</p>
                        <span class="chip">${esc(g.subject || 'General')}</span>
                        <div class="countdown" data-expiry="${g.expires}">${countdown(g.expires)}</div>
                        <div class="muted">${!isExp ? 'until this room vanishes' : 'This conversation has ended'}</div>
                        <div style="margin-top:22px">
                            <span class="muted">♧ ${g.members || 1} members</span>
                            <button class="room-action" onclick="openRoomById(${g.id}, '${esc(g.title)}', true)">
                                ${!isExp ? 'Step inside ↗' : 'Read chat ↗'}
                            </button>
                        </div>
                    </article>
                `;
            }).join('') : `
                <div class="empty" style="grid-column: 1 / -1;">
                    <p style="font-weight:600; margin-bottom: 8px;">No GhostRooms active right now.</p>
                    <small class="muted">Spawn a temporary room for quick doubt clearing or late-night cramming!</small>
                </div>
            `}
        </div>
    `;
}

function modal(html) {
    const body = $('#dialog-body');
    const dlg = $('#dialog');
    if (body && dlg) {
        body.innerHTML = html;
        dlg.showModal();
    }
}

function createGhost() {
    modal(`
        <h2>A little room for something.</h2>
        <p>Pick a topic. Set a timer. Bring your people.</p>
        <form class="form-stack" onsubmit="submitGhost(event)">
            <label>
                Room name
                <input name="title" placeholder="e.g. Midnight Java revision" maxlength="70" required autofocus>
            </label>
            <label>
                What’s the plan?
                <textarea name="topic" maxlength="300" required placeholder="Let people know what to expect."></textarea>
            </label>
            <label>
                Subject
                <select name="subject">
                    ${subjects.map(s => `<option value="${s}">${s}</option>`).join('')}
                </select>
            </label>
            <label>
                Keep it around for
                <select name="hours">
                    <option value="1">1 hour</option>
                    <option value="2" selected>2 hours</option>
                    <option value="6">6 hours</option>
                    <option value="24">24 hours</option>
                </select>
            </label>
            <button class="primary" type="submit">Create GhostRoom ↗</button>
        </form>
    `);
}

async function submitGhost(e) {
    e.preventDefault();
    const f = new FormData(e.target);
    const title = f.get('title').trim();
    const topic = f.get('topic').trim();
    const hours = Number(f.get('hours')) || 2;
    if (!title || !topic) return;

    try {
        const res = await apiPost(`${ctx}/ghostrooms`, {
            name: title,
            duration: `${hours} hours`
        });

        if (res.ok) {
            const data = await res.json();
            $('#dialog').close();
            toast('Your GhostRoom is ready.');
            await loadGhostRooms();
            openRoomById(data.roomId, title, true);
        } else {
            const err = await res.json().catch(() => ({}));
            toast(err.error || 'Failed to create GhostRoom.');
        }
    } catch (err) {
        toast('Network error creating GhostRoom.');
    }
}

// ----------------------------------------------------
// VIEW: DoubtDrop
// ----------------------------------------------------
function renderDoubts() {
    const items = (state.doubts || []).filter(d =>
        (doubtFilter === 'All' || (doubtFilter === 'Resolved' && d.resolved) || (doubtFilter === 'Open' && !d.resolved)) &&
        `${d.title} ${d.body} ${d.subject}`.toLowerCase().includes(doubtSearch.toLowerCase())
    );

    return heading(
        'Big lightbulb energy.',
        'Every “I don’t get it” deserves an “oh, now I do.”',
        `<button class="primary" onclick="askDoubt()">+ Drop a doubt</button>`
    ) + `
        <div class="doubt-controls">
            <input aria-label="Search doubts" placeholder="Search questions, subjects, or concepts…"
                   value="${esc(doubtSearch)}" oninput="doubtSearch=this.value;refreshDoubtsList()">
            <select aria-label="Filter questions" onchange="doubtFilter=this.value;render()">
                ${['All', 'Open', 'Resolved'].map(x => `<option ${x === doubtFilter ? 'selected' : ''}>${x}</option>`).join('')}
            </select>
        </div>
        <div id="doubt-list">${doubtListHTML(items)}</div>
    `;
}

function doubtListHTML(items) {
    if (!items || !items.length) {
        return '<div class="empty">No questions here yet. Be the first to ask.</div>';
    }
    return items.map(d => `
        <article class="doubt-card">
            <span class="chip">${esc(d.subject)}</span>
            <span class="chip ${d.resolved ? 'green' : ''}">${d.resolved ? '✓ Resolved' : 'Open for ideas'}</span>
            <h3>${esc(d.title)}</h3>
            <p>${esc(d.body)}</p>
            <div class="bottom">
                <span class="muted">${esc(d.author)} · ${(d.replies || []).length} ${(d.replies || []).length === 1 ? 'reply' : 'replies'}</span>
                <button class="room-action" onclick="viewDoubt(${d.id})">Join the conversation ↗</button>
            </div>
        </article>
    `).join('');
}

function refreshDoubtsList() {
    const listEl = $('#doubt-list');
    if (!listEl) return;
    const items = (state.doubts || []).filter(d =>
        (doubtFilter === 'All' || (doubtFilter === 'Resolved' && d.resolved) || (doubtFilter === 'Open' && !d.resolved)) &&
        `${d.title} ${d.body} ${d.subject}`.toLowerCase().includes(doubtSearch.toLowerCase())
    );
    listEl.innerHTML = doubtListHTML(items);
}

function askDoubt() {
    modal(`
        <h2>Let’s get you unstuck.</h2>
        <p>Small question, big question. It belongs here.</p>
        <form class="form-stack" onsubmit="submitDoubt(event)">
            <label>
                Subject
                <select name="subject">${subjects.map(s => `<option value="${s}">${s}</option>`).join('')}</select>
            </label>
            <label>
                Your question
                <input name="title" maxlength="150" placeholder="What’s on your mind?" required autofocus>
            </label>
            <label>
                A little more context
                <textarea name="description" maxlength="2000" placeholder="What have you tried? Where are you stuck?" required></textarea>
            </label>
            <button class="primary" type="submit">Drop my doubt ↗</button>
        </form>
    `);
}

async function submitDoubt(e) {
    e.preventDefault();
    const f = new FormData(e.target);
    const subj = f.get('subject');
    const title = f.get('title').trim();
    const desc = f.get('description').trim();
    if (!title || !desc) return;

    try {
        const res = await apiPost(`${ctx}/doubts`, {
            subject: subj,
            title: title,
            description: desc
        });

        if (res.ok) {
            const data = await res.json();
            $('#dialog').close();
            toast('Your question is out there.');
            await loadDoubts();
            navigate('doubts');
            if (data.id) {
                viewDoubt(data.id);
            }
        } else {
            const err = await res.json().catch(() => ({}));
            toast(err.error || 'Failed to post doubt.');
        }
    } catch (err) {
        toast('Network error posting doubt.');
    }
}

async function viewDoubt(id) {
    // Fetch fresh doubt details including all answers from backend
    let d = state.doubts.find(x => Number(x.id) === Number(id));
    try {
        const res = await fetch(`${ctx}/doubts?id=${id}&json=true`);
        if (res.ok) {
            const fresh = await res.json();
            d = fresh;
            // Update in local cache
            const idx = state.doubts.findIndex(x => Number(x.id) === Number(id));
            if (idx >= 0) state.doubts[idx] = fresh;
            else state.doubts.unshift(fresh);
        }
    } catch (e) {}

    if (!d) return;

    modal(`
        <span class="chip">${esc(d.subject)}</span>
        <h2 style="margin-top:15px">${esc(d.title)}</h2>
        <p style="white-space: pre-wrap;">${esc(d.body)}</p>
        <span class="muted">Asked by ${esc(d.author)} · ${d.resolved ? 'Resolved' : 'Open'}</span>

        <div class="answers">
            ${(d.replies || []).length ? d.replies.map(r => `
                <div style="margin-bottom:12px; padding-bottom:8px; border-bottom:1px solid #ece8f2;">
                    <b style="font-size:11px">${esc(r.author)}</b>
                    <p style="margin:4px 0 0; white-space:pre-wrap;">${esc(r.text)}</p>
                </div>
            `).join('') : '<p>No replies yet. A little help starts with you.</p>'}
        </div>

        <form class="form-stack" onsubmit="replyDoubt(event, ${d.id})">
            <label>
                Your reply
                <textarea name="reply" required maxlength="2000" placeholder="Share an explanation or a helpful example…"></textarea>
            </label>
            <button class="primary" type="submit">Post reply ↗</button>
        </form>

        ${d.owned ? `
            <div style="margin-top: 18px; border-top: 1px solid var(--line); padding-top: 12px;">
                <button class="room-action" onclick="resolveDoubt(${d.id})">
                    ${d.resolved ? '✓ Question Resolved' : '✅ Mark as RESOLVED (Author only)'}
                </button>
            </div>
        ` : ''}
    `);
}

async function replyDoubt(e, id) {
    e.preventDefault();
    const val = new FormData(e.target).get('reply').trim();
    if (!val) return;

    try {
        const res = await apiPost(`${ctx}/doubts/answer`, {
            doubtId: id,
            answerText: val
        });

        if (res.ok) {
            toast('A little help, delivered.');
            await loadDoubts();
            viewDoubt(id);
        } else {
            const err = await res.json().catch(() => ({}));
            toast(err.error || 'Failed to submit answer.');
        }
    } catch (err) {
        toast('Network error submitting reply.');
    }
}

async function resolveDoubt(id) {
    try {
        const res = await apiPost(`${ctx}/doubts`, {
            action: 'resolve',
            doubtId: id
        });

        if (res.ok) {
            toast('A lightbulb moment! Marked as resolved.');
            await loadDoubts();
            viewDoubt(id);
        } else {
            const err = await res.json().catch(() => ({}));
            toast(err.error || 'Only the author can resolve this doubt.');
        }
    } catch (err) {
        toast('Network error resolving doubt.');
    }
}

// ----------------------------------------------------
// VIEW: Profile
// ----------------------------------------------------
function renderProfile() {
    return heading('A little more you.', 'Your name and interests help your campus find you.') + `
        <section class="panel profile-panel">
            <h2>Your campus identity</h2>
            <p class="muted">Connected directly to CampusVerse Lite backend session and database.</p>
            <form class="form-stack" onsubmit="saveProfile(event)">
                <label>
                    Your nickname
                    <input name="nickname" maxlength="50" value="${esc(state.profile.name)}" required autofocus>
                </label>
                <div>
                    <label>Your interests</label>
                    <div class="interest-picks">
                        ${state.allInterests.map(i => `
                            <button type="button" class="${state.profile.interests.includes(i) ? 'selected' : ''}" onclick="toggleInterest('${esc(i)}')">
                                ${state.profile.interests.includes(i) ? '✓ ' : '+ '}${esc(i)}
                            </button>
                        `).join('')}
                    </div>
                </div>
                <div style="display:flex; gap:12px; align-items:center;">
                    <button class="primary" type="submit">Save my profile ↗</button>
                    <button type="button" class="room-action" style="color:#b91c1c; border-color:#fca5a5;" onclick="logoutSession()">Log Out</button>
                </div>
            </form>
        </section>
    `;
}

async function saveProfile(e) {
    e.preventDefault();
    const n = new FormData(e.target).get('nickname').trim();
    if (!n) return;

    state.profile.name = n;

    try {
        const res = await apiPost(`${ctx}/student`, {
            action: 'saveProfile',
            nickname: n,
            interests: state.profile.interests
        });

        if (res.ok) {
            toast('Looking like you. Profile saved.');
            render();
            await loadMatches();
        } else {
            const err = await res.json().catch(() => ({}));
            toast(err.error || 'Could not save profile.');
        }
    } catch (err) {
        toast('Network error saving profile.');
    }
}

function logoutSession() {
    // Submit standard form to trigger session invalidation and redirect
    const form = document.createElement('form');
    form.method = 'POST';
    form.action = `${ctx}/student`;

    const aInput = document.createElement('input');
    aInput.type = 'hidden';
    aInput.name = 'action';
    aInput.value = 'logout';

    const cInput = document.createElement('input');
    cInput.type = 'hidden';
    cInput.name = 'csrfToken';
    cInput.value = getCsrfToken();

    form.appendChild(aInput);
    form.appendChild(cInput);
    document.body.appendChild(form);
    form.submit();
}

// ----------------------------------------------------
// VIEW: Saved Activity
// ----------------------------------------------------
function renderSaved() {
    const savedDoubts = (state.doubts || []).filter(d => d.owned);

    return heading('Your little corner of campus.', 'The questions and conversations you’ve started.') + `
        <div class="panel">
            <h2>Your circle</h2>
            ${state.savedCircle.length ? `
                <div class="room-row">
                    <span class="avatar lime">CU</span>
                    <div class="room-info">
                        <b>${state.savedCircle.join(', ')}</b>
                        <p>Saved study partners</p>
                    </div>
                    <button class="room-action" onclick="navigate('match')">View matches ↗</button>
                </div>
            ` : '<p class="muted">Your circle is waiting. Find someone with a shared interest in VibeMatch.</p>'}

            <h2 style="margin-top:28px">Your questions</h2>
            ${doubtListHTML(savedDoubts)}

            <button class="primary" style="margin-top:20px" onclick="navigate('match')">Explore VibeMatch ↗</button>
        </div>
    `;
}

// ----------------------------------------------------
// DATA LOADERS (Connected to Backend Servlets)
// ----------------------------------------------------

async function loadRooms() {
    try {
        const res = await fetch(`${ctx}/chat?rooms=true`);
        if (res.ok) {
            const data = await res.json();
            if (Array.isArray(data)) {
                state.studyRooms = data;
            }
        }
    } catch (ignored) {}
}

async function loadGhostRooms() {
    try {
        const res = await fetch(`${ctx}/ghostrooms?json=true`);
        if (res.ok) {
            const data = await res.json();
            if (Array.isArray(data)) {
                state.ghostRooms = data;
            }
        }
    } catch (ignored) {}
}

async function loadDoubts() {
    try {
        const res = await fetch(`${ctx}/doubts?json=true`);
        if (res.ok) {
            const data = await res.json();
            if (Array.isArray(data)) {
                state.doubts = data;
            }
        }
    } catch (ignored) {}
}

async function loadMatches() {
    try {
        const res = await fetch(`${ctx}/vibematch?json=true`);
        if (res.ok) {
            const data = await res.json();
            if (Array.isArray(data)) {
                state.matches = data;
            }
        }
    } catch (ignored) {}
}

async function loadProfile() {
    try {
        const res = await fetch(`${ctx}/student?json=true`);
        if (res.ok) {
            const data = await res.json();
            if (data.loggedIn) {
                state.profile.name = data.nickname.replace(/_/g, ' ');
                state.profile.interests = data.interests || [];
                if (Array.isArray(data.allInterests) && data.allInterests.length > 0) {
                    state.allInterests = data.allInterests.map(i => i.name);
                }
            }
        }
    } catch (ignored) {}
}

// ----------------------------------------------------
// INITIALIZATION & TIMERS
// ----------------------------------------------------

// Live countdown ticker for GhostRooms (every 1 second)
setInterval(() => {
    document.querySelectorAll('[data-expiry]').forEach(el => {
        el.textContent = countdown(Number(el.dataset.expiry));
    });
}, 1000);

// Hash routing
window.addEventListener('hashchange', () => {
    const p = location.hash.slice(1);
    if (p && p !== current) {
        navigate(p);
    }
});

// Modal outside-click dismiss
const dialog = $('#dialog');
if (dialog) {
    dialog.addEventListener('click', e => {
        if (e.target === dialog) dialog.close();
    });
}

// Initial Boot Sequence
async function boot() {
    await Promise.all([
        loadProfile(),
        loadRooms(),
        loadGhostRooms(),
        loadDoubts(),
        loadMatches()
    ]);

    // Check saved tab or URL hash
    const initialRoute = location.hash.slice(1) || localStorage.getItem('campusverse-pref-tab') || 'home';
    current = ['home', 'chat', 'match', 'ghost', 'doubts', 'profile', 'saved'].includes(initialRoute)
        ? initialRoute
        : 'home';

    render();
}

boot();
