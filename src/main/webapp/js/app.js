/**
 * CampusVerse Lite - Frontend Application
 * Fully integrated Single Page Application (SPA) matching campusverse-illee.berryvenus12.chatgpt.site
 * Connects directly to Java Jakarta EE / Tomcat backend with JDBC H2 persistence and CSRF protection.
 */

const $ = s => document.querySelector(s);
const $$ = s => document.querySelectorAll(s);
const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
}[c]));

// Server context & CSRF token
const ctx = window.APP_CONTEXT || '';
function getCsrfToken() {
    return window.CSRF_TOKEN || $('meta[name="csrf-token"]')?.content || '';
}

// Global state
const defaults = {
    profile: {
        id: (window.INITIAL_STUDENT && window.INITIAL_STUDENT.id) || 1,
        name: (window.INITIAL_STUDENT && window.INITIAL_STUDENT.name) || 'Milee Kesarwani',
        interests: ['Java', 'DSA', 'Hackathons']
    },
    messages: {},
    connections: [],
    ghosts: [],
    doubts: []
};

let state;
try {
    state = { ...defaults, ...JSON.parse(localStorage.getItem('campusverse-v1') || '{}') };
    if (window.INITIAL_STUDENT && window.INITIAL_STUDENT.name) {
        state.profile.name = window.INITIAL_STUDENT.name;
    }
} catch (e) {
    state = structuredClone(defaults);
}

function save() {
    try {
        localStorage.setItem('campusverse-v1', JSON.stringify({
            profile: state.profile,
            connections: state.connections
        }));
    } catch (e) {}
}

function toast(t) {
    const el = $('#toast');
    if (!el) return;
    el.textContent = t;
    el.classList.add('show');
    clearTimeout(window.tt);
    window.tt = setTimeout(() => el.classList.remove('show'), 3000);
}

function initials(s) {
    if (!s) return 'ST';
    return s.replace(/_/g, ' ').split(/\s+/).filter(Boolean).map(x => x[0]).slice(0, 2).join('').toUpperCase();
}

function name() {
    return state.profile.name || 'Milee Kesarwani';
}

const interests = ['Java', 'DSA', 'UI/UX', 'React', 'Hackathons', 'Gaming', 'DBMS', 'Python'];
const navs = [
    ['home', '▦', 'Overview'],
    ['chat', '▤', 'Study Chat'],
    ['match', '✳', 'VibeMatch'],
    ['ghost', '♧', 'GhostRooms'],
    ['doubts', '◈', 'DoubtDrop']
];

let current = 'home';
let room = 'DSA';
let ghostId = null;
let filter = 'All';
let search = '';
const subjects = ['DSA', 'Java', 'DBMS', 'General'];

// Dynamic study room map
let roomMap = { 'Java': 1, 'DSA': 2, 'DBMS': 3, 'General': 4 };

// Peer list populated dynamically from server
let serverPeers = [];
let defaultPeers = [
    { id: 37, name: 'Aman Sharma', initials: 'AS', color: 'peach', score: 67, interests: ['Java', 'React', 'Hackathons'], sharedInterests: ['Java', 'Hackathons'], bio: 'Building things. Breaking things. Learning both ways.', year: 'CSE · 2nd year' },
    { id: 38, name: 'Riya Verma', initials: 'RV', color: 'pink', score: 100, interests: ['Java', 'DSA', 'Hackathons'], sharedInterests: ['Java', 'DSA', 'Hackathons'], bio: 'One more problem before I call it a day.', year: 'CSE · 2nd year' },
    { id: 39, name: 'Kabir Singh', initials: 'KS', color: 'blue', score: 0, interests: ['UI/UX', 'React', 'Gaming'], sharedInterests: [], bio: 'Good interfaces and late-night gaming sessions.', year: 'CSE · 3rd year' },
    { id: 40, name: 'Ananya Das', initials: 'AD', color: 'lime', score: 25, interests: ['DBMS', 'Python', 'DSA'], sharedInterests: ['DSA'], bio: 'Turning confusing concepts into little diagrams.', year: 'CSE · 2nd year' }
];

function getPeers() {
    if (serverPeers && serverPeers.length > 0) return serverPeers;
    return defaultPeers;
}

// Doubts & GhostRooms helpers
function allDoubts() {
    return state.dbDoubts || [];
}

function allGhosts() {
    return state.dbGhosts || [];
}

// Common heading template
function heading(title, sub, action = '') {
    return `<div class="page-heading"><div><div class="eyebrow">A LITTLE COMMUNITY. A LOT OF POSSIBILITY.</div><h1>${title}</h1><p>${sub}</p></div>${action}</div>`;
}

// Router
function navigate(page) {
    if (current === 'chat' && page !== 'chat') {
        stopChatPolling();
    }
    current = page;
    location.hash = page;
    render();
    window.scrollTo(0, 0);
    if (page === 'chat') {
        startChatPolling();
    }
}

function render() {
    const navEl = $('#nav');
    if (navEl) {
        navEl.innerHTML = navs.map(([key, icon, label]) =>
            `<button onclick="navigate('${key}')" class="${current === key ? 'active' : ''}">` +
            `<span class="symbol">${icon}</span><span>${label}</span>` +
            (key === 'ghost' ? '<span class="badge">LIVE</span>' : '') +
            `</button>`
        ).join('');
    }

    const crumbEl = $('#crumb');
    if (crumbEl) {
        crumbEl.textContent = navs.find(x => x[0] === current)?.[2] || (current === 'saved' ? 'Your activity' : 'Your profile');
    }
    const userNameEl = $('#user-name');
    if (userNameEl) userNameEl.textContent = name();

    const userInits = initials(name());
    const headerAvatar = $('#header-avatar');
    if (headerAvatar) headerAvatar.textContent = userInits;
    const sidebarAvatar = $('#sidebar-avatar');
    if (sidebarAvatar) sidebarAvatar.textContent = userInits;

    const views = {
        home: home,
        chat: chat,
        match: match,
        ghost: ghosts,
        doubts: doubts,
        profile: profile,
        saved: saved
    };

    const mainEl = $('#main');
    if (mainEl) {
        mainEl.innerHTML = (views[current] || home)();
    }

    if (current === 'chat') {
        const msgContainer = $('.messages');
        if (msgContainer) msgContainer.scrollTop = 999999;
    }
}

// --- View 1: Overview (Home) ---
function home() {
    const firstName = esc(name().replace(/_/g, ' ').split(/\s+/)[0] || 'Friend');
    const openDoubtsCount = allDoubts().filter(d => !d.resolved).length;
    const liveGhostsCount = allGhosts().filter(g => g.expires > Date.now()).length;
    const peerCount = getPeers().length;

    return heading(
        `Hey ${firstName}, find your people <span style="color:#acb77a">✳</span>`,
        'A little help, a good conversation, and your next big idea. It all starts here.',
        `<span class="date">◷ &nbsp; YOUR CAMPUS, YOUR CORNER</span>`
    ) +
    `<section class="hero">
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
        ${[
            ['♧', 4, 'Study rooms'],
            ['✳', peerCount, 'People to discover'],
            ['◈', openDoubtsCount, 'Open conversations'],
            ['◷', liveGhostsCount, 'GhostRooms live']
        ].map(([i, n, t]) => `
            <div class="stat">
                <span class="stat-icon">${i}</span>
                <div><b>${String(n).padStart(2, '0')}</b><small>${t}</small></div>
            </div>
        `).join('')}
    </section>
    <div class="section-heading">
        <div>
            <h2>Make yourself at home</h2>
            <p>Four ways to make campus feel a little smaller.</p>
        </div>
        <span class="text-link">Pick your vibe ↘</span>
    </div>
    <section class="feature-grid">
        ${[
            ['chat', '▤', 'Study Chat', 'Big questions. Small study circles.<br>Learn better, together.', '4 subject rooms'],
            ['match', '✳', 'VibeMatch', 'Same interests. Fresh connections.<br>Meet your kind of people.', 'Find your circle'],
            ['ghost', '♧', 'GhostRooms', 'Here for a good time, not a long time.<br>Rooms that disappear.', 'Drop in. Talk. Vanish.'],
            ['doubts', '◈', 'DoubtDrop', 'No “silly” questions here.<br>Get unstuck with a little help.', 'Ask away']
        ].map(([p, i, t, d, f]) => `
            <button class="feature" onclick="navigate('${p}')">
                <span class="feature-symbol">${i}</span>
                ${p === 'ghost' ? '<span class="tiny-tag">EPHEMERAL</span>' : ''}
                <h3>${t}</h3>
                <p>${d}</p>
                <div class="feature-bottom"><span>${f}</span><span>↗</span></div>
            </button>
        `).join('')}
    </section>
    <div class="lower-grid">
        <section>
            <div class="section-heading">
                <h2>Find your study corner</h2>
                <button class="text-link" onclick="navigate('chat')">View all rooms ↗</button>
            </div>
            <div class="panel">
                ${[
                    ['DSA', '⌘', 'Data Structures & Algorithms', 'Binary trees & better understanding', '12'],
                    ['Java', '☕', 'The Java Junction', 'Classes, coffee & conversations', '8'],
                    ['DBMS', '▥', 'DBMS Study Circle', 'Making sense of relationships', '6']
                ].map(([s, i, t, d, n]) => `
                    <div class="room-row">
                        <span class="room-icon">${i}</span>
                        <div class="room-info"><b>${t}</b><p><i></i> ${d}</p></div>
                        <div class="faces">
                            <span class="avatar peach">AS</span>
                            <span class="avatar purple">RV</span>
                            <span class="avatar lime">+${n}</span>
                        </div>
                        <button class="room-action" onclick="openRoom('${s}')">Join ↗</button>
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
                ${(() => {
                    const firstDoubt = allDoubts()[0];
                    if (!firstDoubt) {
                        return '<p class="muted">No open doubts right now. Drop one in DoubtDrop!</p>';
                    }
                    const repliesCount = firstDoubt.replies ? firstDoubt.replies.length : (firstDoubt.repliesCount || 0);
                    return `
                        <div class="doubt-teaser">
                            <span class="chip">${esc(firstDoubt.subject)}</span>
                            <span class="chip green">${firstDoubt.resolved ? '✓ Resolved' : 'Open for ideas'}</span>
                            <h3>${esc(firstDoubt.title)}</h3>
                            <div class="doubt-meta">
                                <span><span class="avatar pink" style="width:20px;height:20px;font-size:7px">${esc(initials(firstDoubt.author))}</span> &nbsp; ${esc(firstDoubt.author)}</span>
                                <button onclick="viewDoubt('${firstDoubt.id}')">${repliesCount} ${repliesCount === 1 ? 'reply' : 'replies'} ↗</button>
                            </div>
                        </div>
                        <p class="encourage">Know the answer? <button onclick="viewDoubt('${firstDoubt.id}')">Be someone’s lightbulb moment ↗</button></p>
                    `;
                })()}
            </div>
        </section>
    </div>`;
}

// --- View 2: Study Chat ---
function openRoom(s, id = null) {
    room = s;
    ghostId = id;
    navigate('chat');
}

function chat() {
    let g = ghostId ? allGhosts().find(g => String(g.id) === String(ghostId)) : null;
    const key = ghostId || room;
    const msgs = state.messages[key] || [];
    let expired = g && g.expires <= Date.now();

    return heading(
        'A good place to get it.',
        'Ask a question, share a thought, or just say hello.'
    ) +
    `<div class="info-banner">Connected to CampusVerse Server · Live database messages with 3-second sync.</div>
    <div class="workspace">
        <aside class="panel room-list">
            ${subjects.map(s => `
                <button class="${room === s && !ghostId ? 'active' : ''}" onclick="openRoom('${s}')">
                    # ${s}<small>${s === 'General' ? 'The campus common room' : 'Your ' + s + ' study circle'}</small>
                </button>
            `).join('')}
            ${g ? `<button class="active">♧ ${esc(g.title)}<small>Temporary room</small></button>` : ''}
        </aside>
        <section class="panel chat-panel">
            <div class="chat-top">
                ${g ? '♧ ' + esc(g.title) : '# ' + room + ' Study Circle'}
                <small>${expired ? 'This room has expired. You can still read the conversation.' : 'A little curiosity goes a long way. Be kind, always.'}</small>
            </div>
            <div class="messages" id="chat-messages">
                ${msgs.length === 0 ? `
                    <div class="empty" style="padding:40px 20px;">
                        No messages in #${esc(room)} yet. Be the first to start the conversation!
                    </div>
                ` : msgs.map(m => {
                    const isMine = m.author === name() || m.senderNickname === name();
                    const color = isMine ? 'lime' : 'purple';
                    return `
                        <div class="message ${isMine ? 'mine' : ''}">
                            <span class="avatar ${color}">${esc(initials(m.author || m.senderNickname))}</span>
                            <div>
                                <b>${esc(m.author || m.senderNickname)}</b><time>${esc(m.time || m.createdAt || 'Just now')}</time>
                                <div class="bubble">${esc(m.text || m.content)}</div>
                            </div>
                        </div>
                    `;
                }).join('')}
            </div>
            <form class="compose" onsubmit="sendMessage(event)">
                <input name="message" id="message-input" maxlength="2000" aria-label="Message"
                       placeholder="${expired ? 'This GhostRoom is closed' : 'A question? A thought? Drop it here…'}"
                       required ${expired ? 'disabled' : ''} autocomplete="off">
                <button type="submit" class="primary" ${expired ? 'disabled' : ''}>Send ↗</button>
            </form>
        </section>
    </div>`;
}

let chatPollInterval = null;

function startChatPolling() {
    stopChatPolling();
    loadRemoteMessages();
    chatPollInterval = setInterval(loadRemoteMessages, 3000);
}

function stopChatPolling() {
    if (chatPollInterval) {
        clearInterval(chatPollInterval);
        chatPollInterval = null;
    }
}

async function loadRemoteMessages() {
    if (current !== 'chat') return;
    const key = ghostId || room;
    const numId = ghostId ? parseInt(ghostId.replace(/\D/g, '') || '999') : (roomMap[room] || 1);

    try {
        const res = await fetch(`${ctx}/api/messages?roomId=${numId}`, {
            headers: { 'Accept': 'application/json' }
        });
        if (res.ok) {
            const list = await res.json();
            if (Array.isArray(list)) {
                const mapped = list.map(m => ({
                    id: m.id,
                    author: m.senderNickname || 'Student',
                    senderNickname: m.senderNickname || 'Student',
                    text: m.content,
                    content: m.content,
                    time: m.createdAt ? m.createdAt.slice(11, 16) : 'Just now',
                    createdAt: m.createdAt
                }));
                state.messages[key] = mapped;

                const msgBox = $('#chat-messages');
                if (msgBox) {
                    const wasAtBottom = (msgBox.scrollHeight - msgBox.scrollTop - msgBox.clientHeight) < 60;
                    if (mapped.length === 0) {
                        msgBox.innerHTML = `<div class="empty" style="padding:40px 20px;">No messages in #${esc(room)} yet. Be the first to start the conversation!</div>`;
                    } else {
                        msgBox.innerHTML = mapped.map(m => {
                            const isMine = m.author === name() || m.senderNickname === name();
                            const color = isMine ? 'lime' : 'purple';
                            return `
                                <div class="message ${isMine ? 'mine' : ''}">
                                    <span class="avatar ${color}">${esc(initials(m.author))}</span>
                                    <div>
                                        <b>${esc(m.author)}</b><time>${esc(m.time)}</time>
                                        <div class="bubble">${esc(m.text)}</div>
                                    </div>
                                </div>
                            `;
                        }).join('');
                    }
                    if (wasAtBottom) msgBox.scrollTop = msgBox.scrollHeight;
                }
            }
        }
    } catch (e) {}
}

async function sendMessage(e) {
    e.preventDefault();
    if (ghostId) {
        const g = allGhosts().find(g => String(g.id) === String(ghostId));
        if (g && g.expires <= Date.now()) {
            toast('This room has expired.');
            render();
            return;
        }
    }

    const input = $('#message-input');
    const text = input ? input.value.trim() : '';
    if (!text) return;

    const key = ghostId || room;
    const numId = ghostId ? parseInt(ghostId.replace(/\D/g, '') || '999') : (roomMap[room] || 1);

    try {
        const token = getCsrfToken();
        const bodyParams = new URLSearchParams();
        bodyParams.append('roomId', String(numId));
        bodyParams.append('content', text);
        bodyParams.append('csrfToken', token);

        const res = await fetch(`${ctx}/messages`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'Accept': 'application/json',
                'X-Requested-With': 'XMLHttpRequest',
                'X-CSRF-TOKEN': token
            },
            body: bodyParams.toString()
        });

        if (!res.ok) {
            const errJson = await res.json().catch(() => ({}));
            toast(errJson.error || 'Failed to send message.');
        } else {
            if (input) input.value = '';
            await loadRemoteMessages();
        }
    } catch (err) {
        toast('Connection error. Message draft preserved.');
    }
}

// --- View 3: VibeMatch ---
function match() {
    const chosen = state.profile.interests;
    const peersList = getPeers();

    const list = peersList.map(p => {
        let score = p.score;
        if (score === undefined) {
            const intersect = p.interests.filter(i => chosen.includes(i)).length;
            const union = new Set([...p.interests, ...chosen]).size;
            score = union === 0 ? 0 : Math.round((intersect / union) * 100);
        }
        const color = p.color || (['peach', 'pink', 'blue', 'lime'][Math.abs(p.name.length) % 4]);
        return { ...p, score, color };
    }).sort((a, b) => b.score - a.score);

    return heading(
        'Your people are out there.',
        'Start with what you love. Find someone who gets it.'
    ) +
    `<div class="info-banner">VibeMatch calculates interest overlap using Jaccard Similarity (Intersection ÷ Union). Choose your interests below.</div>
    <div class="section-heading">
        <h2>What’s your thing?</h2>
        <span class="muted">Select a few. We’ll find the overlap.</span>
    </div>
    <div class="interest-picks">
        ${interests.map(i => `
            <button class="${chosen.includes(i) ? 'selected' : ''}" onclick="toggleInterest('${i}')">
                ${chosen.includes(i) ? '✓ ' : '+ '}${i}
            </button>
        `).join('')}
    </div>
    <div class="card-grid">
        ${list.length === 0 ? `
            <div class="empty">No classmates discovered yet. Invite peers to explore CampusVerse!</div>
        ` : list.map(p => `
            <article class="person">
                <span class="match">${p.score}% match</span>
                <span class="avatar ${p.color}">${esc(p.initials)}</span>
                <h3>${esc(p.name)}</h3>
                <span class="muted">${esc(p.year || 'CSE · 2nd year')}</span>
                <p>${esc(p.bio || 'Building things. Learning together on CampusVerse.')}</p>
                <div>
                    ${p.interests.map(i => `<span class="chip ${chosen.includes(i) ? 'green' : ''}">${esc(i)}</span>`).join('')}
                </div>
                <button class="primary" onclick="connect('${p.initials}')">
                    ${state.connections.includes(p.initials) ? '✓ Saved to your circle' : 'Add to my circle ↗'}
                </button>
            </article>
        `).join('')}
    </div>`;
}

async function toggleInterest(i) {
    const a = state.profile.interests;
    state.profile.interests = a.includes(i) ? a.filter(x => x !== i) : [...a, i];
    save();
    render();

    try {
        const token = getCsrfToken();
        const p = new URLSearchParams();
        p.append('action', 'update_profile');
        p.append('nickname', name());
        p.append('csrfToken', token);
        state.profile.interests.forEach(it => p.append('interests', it));

        await fetch(`${ctx}/student`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'Accept': 'application/json',
                'X-CSRF-TOKEN': token
            },
            body: p.toString()
        });

        await fetchVibeMatches();
        if (current === 'match') render();
    } catch (e) {}
}

async function fetchVibeMatches() {
    try {
        const res = await fetch(`${ctx}/vibematch?json=true`, {
            headers: { 'Accept': 'application/json' }
        });
        if (res.ok) {
            const data = await res.json();
            if (Array.isArray(data) && data.length > 0) {
                serverPeers = data;
            }
        }
    } catch (e) {}
}

function connect(inits) {
    if (state.connections.includes(inits)) {
        state.connections = state.connections.filter(x => x !== inits);
        toast('Removed from your circle.');
    } else {
        state.connections.push(inits);
        toast('Added to your circle.');
    }
    save();
    render();
}

// --- View 4: GhostRooms ---
function countdown(t) {
    let s = Math.max(0, Math.floor((t - Date.now()) / 1000));
    if (!s) return 'Room closed';
    const hrs = String(Math.floor(s / 3600)).padStart(2, '0');
    const mins = String(Math.floor(s / 60) % 60).padStart(2, '0');
    const secs = String(s % 60).padStart(2, '0');
    return `${hrs} : ${mins} : ${secs}`;
}

function ghosts() {
    const ghostList = allGhosts();

    return heading(
        'Good conversations. Limited editions.',
        'A room for right now. Revision, ideas, or whatever’s on your mind.',
        `<button class="primary" onclick="createGhost()">+ Create a room</button>`
    ) +
    `<div class="info-banner">GhostRooms close when their timer runs out. Messages stay readable, but no new messages can be sent.</div>
    <div class="card-grid">
        ${ghostList.length === 0 ? `
            <div class="empty">No active GhostRooms right now. Create one above to start a study sprint!</div>
        ` : ghostList.map(g => `
            <article class="ghost-card">
                <div class="ghost" aria-hidden="true"></div>
                <h3>${esc(g.title)}</h3>
                <p>${esc(g.topic)}</p>
                <span class="chip">${esc(g.subject || 'General')}</span>
                <div class="countdown" data-expiry="${g.expires}">${countdown(g.expires)}</div>
                <div class="muted">${g.expires > Date.now() ? 'until this room vanishes' : 'This conversation has ended'}</div>
                <div style="margin-top:22px">
                    <span class="muted">♧ ${g.members || 4} members</span>
                    <button class="room-action" onclick="openRoom('${g.subject || 'General'}','${g.id}')">
                        ${g.expires > Date.now() ? 'Step inside ↗' : 'Read chat ↗'}
                    </button>
                </div>
            </article>
        `).join('')}
    </div>`;
}

function modal(html) {
    const b = $('#dialog-body');
    const d = $('#dialog');
    if (b && d) {
        b.innerHTML = html;
        d.showModal();
    }
}

function createGhost() {
    modal(`
        <h2>A little room for something.</h2>
        <p>Pick a topic. Set a timer. Bring your people.</p>
        <form class="form-stack" onsubmit="submitGhost(event)">
            <label>Room name
                <input name="title" placeholder="e.g. Midnight Java revision" maxlength="70" required autofocus>
            </label>
            <label>What’s the plan?
                <textarea name="topic" maxlength="300" required placeholder="Let people know what to expect."></textarea>
            </label>
            <label>Subject
                <select name="subject">${subjects.map(s => `<option>${s}</option>`).join('')}</select>
            </label>
            <label>Keep it around for
                <select name="hours">
                    <option value="1">1 hour</option>
                    <option value="2" selected>2 hours</option>
                    <option value="6">6 hours</option>
                    <option value="24">24 hours</option>
                </select>
            </label>
            <button type="submit" class="primary">Create GhostRoom ↗</button>
        </form>
    `);
}

async function submitGhost(e) {
    e.preventDefault();
    const f = new FormData(e.target);
    const title = f.get('title').trim();
    const topic = f.get('topic').trim();
    const subject = f.get('subject') || 'General';
    const hours = f.get('hours') || '2';
    if (!title || !topic) return;

    try {
        const token = getCsrfToken();
        const p = new URLSearchParams();
        p.append('action', 'create');
        p.append('title', title);
        p.append('topic', topic);
        p.append('subject', subject);
        p.append('hours', hours);
        p.append('csrfToken', token);

        await fetch(`${ctx}/ghostrooms`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'Accept': 'application/json',
                'X-CSRF-TOKEN': token
            },
            body: p.toString()
        });

        await fetchGhostRooms();
    } catch (err) {}

    $('#dialog').close();
    navigate('ghost');
    toast('Your GhostRoom is ready.');
}

async function fetchGhostRooms() {
    try {
        const res = await fetch(`${ctx}/ghostrooms?json=true`, {
            headers: { 'Accept': 'application/json' }
        });
        if (res.ok) {
            const data = await res.json();
            if (Array.isArray(data)) {
                state.dbGhosts = data.map(g => ({
                    id: String(g.id),
                    title: g.title,
                    topic: g.topic || 'Live temporary study room',
                    subject: g.subject || 'General',
                    expires: g.expires,
                    members: g.members || 4
                }));
            }
        }
    } catch (e) {}
}

// --- View 5: DoubtDrop ---
function doubts() {
    let items = allDoubts().filter(d =>
        (filter === 'All' || (filter === 'Resolved' && d.resolved) || (filter === 'Open' && !d.resolved)) &&
        `${d.title} ${d.body} ${d.subject}`.toLowerCase().includes(search.toLowerCase())
    );

    return heading(
        'Big lightbulb energy.',
        'Every “I don’t get it” deserves an “oh, now I do.”',
        `<button class="primary" onclick="askDoubt()">+ Drop a doubt</button>`
    ) +
    `<div class="doubt-controls">
        <input aria-label="Search doubts" placeholder="Search questions, subjects, or concepts…"
               value="${esc(search)}" oninput="search=this.value;refreshDoubts()">
        <select aria-label="Filter questions" onchange="filter=this.value;render()">
            ${['All', 'Open', 'Resolved'].map(x => `<option ${x === filter ? 'selected' : ''}>${x}</option>`).join('')}
        </select>
    </div>
    <div id="doubt-list">${doubtList(items)}</div>`;
}

function doubtList(items) {
    if (!items || !items.length) {
        return '<div class="empty">No questions here yet. Be the first to ask.</div>';
    }
    return items.map(d => {
        const replyCount = d.replies ? d.replies.length : (d.repliesCount || 0);
        return `
            <article class="doubt-card">
                <span class="chip">${esc(d.subject)}</span>
                <span class="chip ${d.resolved ? 'green' : ''}">${d.resolved ? '✓ Resolved' : 'Open for ideas'}</span>
                <h3>${esc(d.title)}</h3>
                <p>${esc(d.body || d.description || '')}</p>
                <div class="bottom">
                    <span class="muted">${esc(d.author || 'Peer')} · ${replyCount} ${replyCount === 1 ? 'reply' : 'replies'}</span>
                    <button class="room-action" onclick="viewDoubt('${d.id}')">Join the conversation ↗</button>
                </div>
            </article>
        `;
    }).join('');
}

function refreshDoubts() {
    const listEl = $('#doubt-list');
    if (!listEl) return;
    const items = allDoubts().filter(d =>
        (filter === 'All' || (filter === 'Resolved' && d.resolved) || (filter === 'Open' && !d.resolved)) &&
        `${d.title} ${d.body} ${d.subject}`.toLowerCase().includes(search.toLowerCase())
    );
    listEl.innerHTML = doubtList(items);
}

function askDoubt() {
    modal(`
        <h2>Let’s get you unstuck.</h2>
        <p>Small question, big question. It belongs here.</p>
        <form class="form-stack" onsubmit="submitDoubt(event)">
            <label>Subject
                <select name="subject">${subjects.map(s => `<option>${s}</option>`).join('')}</select>
            </label>
            <label>Your question
                <input name="title" maxlength="150" placeholder="What’s on your mind?" required autofocus>
            </label>
            <label>A little more context
                <textarea name="body" maxlength="2000" placeholder="What have you tried? Where are you stuck?" required></textarea>
            </label>
            <button type="submit" class="primary">Drop my doubt ↗</button>
        </form>
    `);
}

async function submitDoubt(e) {
    e.preventDefault();
    const f = new FormData(e.target);
    const subject = f.get('subject') || 'General';
    const title = f.get('title').trim();
    const body = f.get('body').trim();
    if (!title || !body) return;

    try {
        const token = getCsrfToken();
        const p = new URLSearchParams();
        p.append('action', 'create');
        p.append('subject', subject);
        p.append('title', title);
        p.append('description', body);
        p.append('body', body);
        p.append('csrfToken', token);

        await fetch(`${ctx}/doubts`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'Accept': 'application/json',
                'X-CSRF-TOKEN': token
            },
            body: p.toString()
        });

        await fetchDoubts();
    } catch (err) {}

    $('#dialog').close();
    navigate('doubts');
    toast('Your question is out there.');
}

async function fetchDoubts() {
    try {
        const res = await fetch(`${ctx}/doubts?json=true`, {
            headers: { 'Accept': 'application/json' }
        });
        if (res.ok) {
            const data = await res.json();
            if (Array.isArray(data)) {
                state.dbDoubts = data.map(d => ({
                    id: d.id,
                    subject: d.subject,
                    title: d.title,
                    body: d.body || d.description,
                    author: d.author,
                    resolved: d.resolved,
                    owned: d.owned,
                    replies: d.replies || []
                }));
            }
        }
    } catch (e) {}
}

function viewDoubt(id) {
    const d = allDoubts().find(d => String(d.id) === String(id));
    if (!d) return;
    const isOwner = d.owned || d.author === name();
    const replies = d.replies || [];

    modal(`
        <span class="chip">${esc(d.subject)}</span>
        <h2 style="margin-top:15px">${esc(d.title)}</h2>
        <p>${esc(d.body || d.description || '')}</p>
        <span class="muted">Asked by ${esc(d.author)} · ${d.resolved ? 'Resolved' : 'Open'}</span>
        <div class="answers">
            ${replies.length ? replies.map(r => `
                <b style="font-size:11px">${esc(r.author || 'Peer')}</b>
                <p>${esc(r.text || r.answerText)}</p>
            `).join('') : '<p>No replies yet. A little help starts with you.</p>'}
        </div>
        <form class="form-stack" onsubmit="replyDoubt(event,'${d.id}')">
            <label>Your reply
                <textarea name="reply" required maxlength="2000" placeholder="Share an explanation or a helpful example…"></textarea>
            </label>
            <button type="submit" class="primary">Post reply ↗</button>
        </form>
        ${isOwner ? `
            <button class="room-action" style="margin-top:15px" onclick="resolveDoubt('${d.id}')">
                ${d.resolved ? 'Reopen question' : '✓ Mark as resolved'}
            </button>
        ` : ''}
    `);
}

async function replyDoubt(e, id) {
    e.preventDefault();
    const val = new FormData(e.target).get('reply').trim();
    if (!val) return;

    try {
        const token = getCsrfToken();
        const p = new URLSearchParams();
        p.append('doubtId', String(id).replace(/\D/g, '') || '1');
        p.append('answerText', val);
        p.append('reply', val);
        p.append('csrfToken', token);

        await fetch(`${ctx}/doubts/answer`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'Accept': 'application/json',
                'X-CSRF-TOKEN': token
            },
            body: p.toString()
        });

        await fetchDoubts();
    } catch (err) {}

    $('#dialog').close();
    render();
    viewDoubt(id);
    toast('A little help, delivered.');
}

async function resolveDoubt(id) {
    try {
        const token = getCsrfToken();
        const p = new URLSearchParams();
        p.append('action', 'resolve');
        p.append('doubtId', String(id).replace(/\D/g, '') || '1');
        p.append('csrfToken', token);

        await fetch(`${ctx}/doubts`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'Accept': 'application/json',
                'X-CSRF-TOKEN': token
            },
            body: p.toString()
        });

        await fetchDoubts();
    } catch (err) {}

    $('#dialog').close();
    render();
    toast('Doubt status updated.');
}

// --- View 6: Profile ---
function profile() {
    return heading(
        'A little more you.',
        'Your name and interests help your campus find you.'
    ) +
    `<section class="panel profile-panel">
        <h2>Your campus identity</h2>
        <p class="muted">Connected directly to Chandigarh University CampusVerse server.</p>
        <form class="form-stack" onsubmit="saveProfile(event)">
            <label>Your nickname
                <input name="nickname" maxlength="50" value="${esc(name())}" required autofocus>
            </label>
            <div>
                <label>Your interests</label>
                <div class="interest-picks">
                    ${interests.map(i => `
                        <button type="button" class="${state.profile.interests.includes(i) ? 'selected' : ''}" onclick="toggleInterest('${i}')">
                            ${state.profile.interests.includes(i) ? '✓ ' : '+ '}${i}
                        </button>
                    `).join('')}
                </div>
            </div>
            <button type="submit" class="primary">Save my profile ↗</button>
        </form>
    </section>`;
}

async function saveProfile(e) {
    e.preventDefault();
    const n = new FormData(e.target).get('nickname').trim();
    if (!n) return;

    state.profile.name = n;
    save();
    render();

    try {
        const token = getCsrfToken();
        const p = new URLSearchParams();
        p.append('action', 'update_profile');
        p.append('nickname', n);
        p.append('csrfToken', token);
        state.profile.interests.forEach(it => p.append('interests', it));

        await fetch(`${ctx}/student`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'Accept': 'application/json',
                'X-CSRF-TOKEN': token
            },
            body: p.toString()
        });
        await fetchVibeMatches();
    } catch (err) {}

    toast('Looking like you. Profile saved.');
}

// --- View 7: Saved Activity ---
function saved() {
    const savedPeers = getPeers().filter(p => state.connections.includes(p.initials));
    const userDoubts = allDoubts().filter(d => d.owned || d.author === name());

    return heading(
        'Your little corner of campus.',
        'The people, questions, and conversations you’ve started.'
    ) +
    `<div class="panel">
        <h2>Your circle</h2>
        ${savedPeers.length ? savedPeers.map(p => `
            <div class="room-row">
                <span class="avatar ${p.color || 'purple'}">${esc(p.initials)}</span>
                <div class="room-info">
                    <b>${esc(p.name)}</b>
                    <p>${esc((p.interests || []).join(' · '))}</p>
                </div>
                <button class="room-action" onclick="navigate('match')">View matches ↗</button>
            </div>
        `).join('') : '<p class="muted">Your circle is waiting. Find someone with a shared interest in VibeMatch.</p>'}

        <h2 style="margin-top:28px">Your questions</h2>
        ${doubtList(userDoubts)}
        <button class="primary" style="margin-top:20px" onclick="navigate('match')">Explore VibeMatch ↗</button>
    </div>`;
}

// --- Initialization & Global Listeners ---

setInterval(() => {
    document.querySelectorAll('[data-expiry]').forEach(el => {
        el.textContent = countdown(Number(el.dataset.expiry));
    });
    if (current === 'chat' && ghostId) {
        const g = allGhosts().find(g => String(g.id) === String(ghostId));
        const input = $('.compose input');
        if (g && g.expires <= Date.now() && input && !input.disabled) {
            render();
        }
    }
}, 1000);

window.addEventListener('hashchange', () => {
    const p = location.hash.slice(1);
    if (p && p !== current) {
        navigate(p);
    }
});

$('#dialog')?.addEventListener('click', e => {
    if (e.target === $('#dialog')) $('#dialog').close();
});

current = location.hash.slice(1) || 'home';
render();

// Fetch initial data from server on startup
(async function init() {
    await Promise.all([
        fetchDoubts(),
        fetchGhostRooms(),
        fetchVibeMatches(),
        (async () => {
            try {
                const res = await fetch(`${ctx}/chat?rooms=true`, { headers: { 'Accept': 'application/json' } });
                if (res.ok) {
                    const data = await res.json();
                    if (Array.isArray(data)) {
                        data.forEach(r => { roomMap[r.name] = r.id; });
                    }
                }
            } catch (e) {}
        })()
    ]);
    render();
})();
