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
    localStorage.setItem('campusverse-v1', JSON.stringify(state));
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

// Study rooms database ID map
let roomMap = { 'Java': 1, 'DSA': 2, 'DBMS': 3, 'General': 4 };

// Known peer profiles for VibeMatch
let peers = [
    { name: 'Aman Sharma', initials: 'AS', color: 'peach', interests: ['Java', 'React', 'Hackathons'], bio: 'Building things. Breaking things. Learning both ways.', year: 'CSE · 2nd year' },
    { name: 'Riya Verma', initials: 'RV', color: 'pink', interests: ['Java', 'DSA', 'Hackathons'], bio: 'One more problem before I call it a day.', year: 'CSE · 2nd year' },
    { name: 'Kabir Singh', initials: 'KS', color: 'blue', interests: ['UI/UX', 'React', 'Gaming'], bio: 'Good interfaces and late-night gaming sessions.', year: 'CSE · 3rd year' },
    { name: 'Ananya Das', initials: 'AD', color: 'lime', interests: ['DBMS', 'Python', 'DSA'], bio: 'Turning confusing concepts into little diagrams.', year: 'CSE · 2nd year' }
];

// Fallback seed doubts
const seedDoubts = [
    {
        id: 'seed1',
        subject: 'Java',
        title: 'Can someone explain method overloading with a real-life example?',
        body: 'I understand the syntax, but when should I use it instead of creating a new method?',
        author: 'Riya Verma',
        resolved: false,
        replies: [{ author: 'Aman Sharma', text: 'Use overloading when the operation is the same but the input differs — like add(int, int) and add(double, double). Java picks the method from the argument types.' }]
    },
    {
        id: 'seed2',
        subject: 'DBMS',
        title: 'A simple way to remember 2NF vs 3NF?',
        body: 'Partial and transitive dependencies keep getting mixed up in my head. Any good examples?',
        author: 'Ananya Das',
        resolved: false,
        replies: []
    },
    {
        id: 'seed3',
        subject: 'DSA',
        title: 'Why is binary search O(log n)?',
        body: 'What happens to the search space in each step?',
        author: 'Kabir Singh',
        resolved: true,
        replies: [{ author: 'Riya Verma', text: 'Each comparison halves the search space. After k steps, n / 2^k = 1, so k = log₂(n).' }]
    }
];

let baseTime = Number(localStorage.getItem('campusverse-start')) || Date.now();
localStorage.setItem('campusverse-start', baseTime);

const seedGhosts = [
    { id: 'dbms', title: 'DBMS: the last-minute club', topic: 'Normalization, SQL & a little moral support.', subject: 'DBMS', expires: baseTime + 7200000, members: 8 },
    { id: 'algo', title: 'Two pointers, one brain cell', topic: 'Let’s work through the DSA practice sheet.', subject: 'DSA', expires: baseTime + 14400000, members: 5 },
    { id: 'hack', title: 'Hackathon idea jam', topic: 'No bad ideas. Just bring your curiosity.', subject: 'General', expires: baseTime + 28800000, members: 6 }
];

// Combine remote/local records
function allDoubts() {
    if (state.dbDoubts && state.dbDoubts.length > 0) {
        return state.dbDoubts;
    }
    return [...state.doubts, ...seedDoubts.map(d => ({ ...d, ...(state['doubt-' + d.id] || {}) }))];
}

function updateDoubt(d) {
    if (String(d.id).startsWith('seed')) {
        state['doubt-' + d.id] = d;
    } else {
        const idx = state.doubts.findIndex(x => String(x.id) === String(d.id));
        if (idx !== -1) state.doubts[idx] = d;
        else state.doubts.unshift(d);
    }
    save();
}

function allGhosts() {
    if (state.dbGhosts && state.dbGhosts.length > 0) {
        return state.dbGhosts;
    }
    return [...state.ghosts, ...seedGhosts];
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
    // 1. Render navigation
    const navEl = $('#nav');
    if (navEl) {
        navEl.innerHTML = navs.map(([key, icon, label]) =>
            `<button onclick="navigate('${key}')" class="${current === key ? 'active' : ''}">` +
            `<span class="symbol">${icon}</span><span>${label}</span>` +
            (key === 'ghost' ? '<span class="badge">LIVE</span>' : '') +
            `</button>`
        ).join('');
    }

    // 2. Breadcrumbs & Profile headers
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

    // 3. Render View
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
            ['✳', peers.length, 'People to discover'],
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
                <div class="doubt-teaser">
                    <span class="chip">Java</span>
                    <span class="chip green">Open for ideas</span>
                    <h3>Can someone explain method overloading with a real-life example?</h3>
                    <div class="doubt-meta">
                        <span><span class="avatar pink" style="width:20px;height:20px;font-size:7px">RV</span> &nbsp; Riya Verma</span>
                        <button onclick="viewDoubt('seed1')">1 reply ↗</button>
                    </div>
                </div>
                <p class="encourage">Know the answer? <button onclick="viewDoubt('seed1')">Be someone’s lightbulb moment ↗</button></p>
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
                ${!g ? `
                    <div class="message">
                        <span class="avatar pink">RV</span>
                        <div>
                            <b>Riya Verma</b><time>Earlier</time>
                            <div class="bubble">${
                                room === 'DSA' ? 'Anyone up for working through binary search together? I keep mixing up the boundary conditions.' :
                                room === 'Java' ? 'Welcome to the Java Junction! What are you working on today?' :
                                room === 'DBMS' ? 'Revision check-in: joins, normalization, or transactions? Where should we start?' :
                                'Hey everyone! What’s one thing you learned today?'
                            }</div>
                        </div>
                    </div>
                    <div class="message">
                        <span class="avatar peach">AS</span>
                        <div>
                            <b>Aman Sharma</b><time>Earlier</time>
                            <div class="bubble">${
                                room === 'DSA' ? 'I’m in! Start with low = 0 and high = n − 1, and keep going while low ≤ high. Let’s try a small example.' :
                                'Happy to help! Drop your questions here and we can figure them out together.'
                            }</div>
                        </div>
                    </div>
                ` : '<p class="muted">This room is temporary. Make the conversation count.</p>'}
                ${msgs.map(m => {
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
                save();

                // Append or refresh without losing input focus
                const msgBox = $('#chat-messages');
                if (msgBox) {
                    const wasAtBottom = (msgBox.scrollHeight - msgBox.scrollTop - msgBox.clientHeight) < 60;
                    // Re-render message list
                    const rendered = (key && !ghostId ? `
                        <div class="message"><span class="avatar pink">RV</span><div><b>Riya Verma</b><time>Earlier</time><div class="bubble">${room === 'DSA' ? 'Anyone up for working through binary search together? I keep mixing up boundary conditions.' : 'Welcome to the room! Drop your questions here.'}</div></div>
                        <div class="message"><span class="avatar peach">AS</span><div><b>Aman Sharma</b><time>Earlier</time><div class="bubble">Happy to collaborate. Let’s work through examples together.</div></div>
                    ` : '') + mapped.map(m => {
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
                    msgBox.innerHTML = rendered;
                    if (wasAtBottom) msgBox.scrollTop = msgBox.scrollHeight;
                }
            }
        }
    } catch (e) {
        // Silently tolerate temporary polling errors
    }
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

    // Optimistically show message
    const tempMsg = {
        author: name(),
        senderNickname: name(),
        text,
        content: text,
        time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    };
    (state.messages[key] ??= []).push(tempMsg);
    save();
    render();

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
            loadRemoteMessages();
        }
    } catch (err) {
        toast('Message saved locally (offline mode).');
    }
}

// --- View 3: VibeMatch ---
function match() {
    const chosen = state.profile.interests;
    const list = peers.map(p => {
        const intersect = p.interests.filter(i => chosen.includes(i)).length;
        const union = new Set([...p.interests, ...chosen]).size;
        const score = union === 0 ? 0 : Math.round((intersect / union) * 100);
        return { ...p, score };
    }).sort((a, b) => b.score - a.score);

    return heading(
        'Your people are out there.',
        'Start with what you love. Find someone who gets it.'
    ) +
    `<div class="info-banner">VibeMatch compares shared interests using intersection ÷ union. Choose your interests below to see matches update.</div>
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
        ${list.map(p => `
            <article class="person">
                <span class="match">${p.score}% match</span>
                <span class="avatar ${p.color}">${p.initials}</span>
                <h3>${p.name}</h3>
                <span class="muted">${p.year}</span>
                <p>${p.bio}</p>
                <div>
                    ${p.interests.map(i => `<span class="chip ${chosen.includes(i) ? 'green' : ''}">${i}</span>`).join('')}
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

    // Sync interests to backend
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
    return heading(
        'Good conversations. Limited editions.',
        'A room for right now. Revision, ideas, or whatever’s on your mind.',
        `<button class="primary" onclick="createGhost()">+ Create a room</button>`
    ) +
    `<div class="info-banner">GhostRooms close when their timer runs out. Messages stay readable, but no new messages can be sent.</div>
    <div class="card-grid">
        ${allGhosts().map(g => `
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

    const newGhost = {
        id: 'g' + Date.now(),
        title,
        topic,
        subject,
        expires: Date.now() + Number(hours) * 3600000,
        members: 1
    };
    state.ghosts.unshift(newGhost);
    save();

    // Call server to persist room
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
    } catch (err) {}

    $('#dialog').close();
    navigate('ghost');
    toast('Your GhostRoom is ready.');
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

    const newDoubt = {
        id: 'd' + Date.now(),
        subject,
        title,
        body,
        description: body,
        author: name(),
        owned: true,
        resolved: false,
        replies: []
    };
    state.doubts.unshift(newDoubt);
    save();

    // Post to Java backend
    try {
        const token = getCsrfToken();
        const p = new URLSearchParams();
        p.append('action', 'create');
        p.append('subject', subject);
        p.append('title', title);
        p.append('description', body);
        p.append('body', body);
        p.append('csrfToken', token);

        const res = await fetch(`${ctx}/doubts`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'Accept': 'application/json',
                'X-CSRF-TOKEN': token
            },
            body: p.toString()
        });
        if (res.ok) {
            const data = await res.json();
            if (data.id) newDoubt.id = data.id;
        }
    } catch (err) {}

    $('#dialog').close();
    navigate('doubts');
    toast('Your question is out there.');
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

    const d = allDoubts().find(d => String(d.id) === String(id));
    if (!d) return;

    d.replies ??= [];
    d.replies.push({ author: name(), text: val, answerText: val });
    updateDoubt(d);

    // Call server API
    try {
        const token = getCsrfToken();
        const p = new URLSearchParams();
        p.append('doubtId', String(d.id).replace(/\D/g, '') || '1');
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
    } catch (err) {}

    $('#dialog').close();
    render();
    viewDoubt(id);
    toast('A little help, delivered.');
}

async function resolveDoubt(id) {
    const d = allDoubts().find(d => String(d.id) === String(id));
    if (!d) return;

    d.resolved = !d.resolved;
    d.status = d.resolved ? 'RESOLVED' : 'OPEN';
    updateDoubt(d);

    try {
        const token = getCsrfToken();
        const p = new URLSearchParams();
        p.append('action', 'resolve');
        p.append('doubtId', String(d.id).replace(/\D/g, '') || '1');
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
    } catch (err) {}

    $('#dialog').close();
    render();
    toast(d.resolved ? 'A lightbulb moment! Marked resolved.' : 'Question reopened.');
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
    } catch (err) {}

    toast('Looking like you. Profile saved.');
}

// --- View 7: Saved Activity ---
function saved() {
    const savedPeers = peers.filter(p => state.connections.includes(p.initials));
    const userDoubts = allDoubts().filter(d => d.owned || d.author === name());

    return heading(
        'Your little corner of campus.',
        'The people, questions, and conversations you’ve started.'
    ) +
    `<div class="panel">
        <h2>Your circle</h2>
        ${savedPeers.length ? savedPeers.map(p => `
            <div class="room-row">
                <span class="avatar ${p.color}">${p.initials}</span>
                <div class="room-info">
                    <b>${p.name}</b>
                    <p>${p.interests.join(' · ')}</p>
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

// 1. Ticking countdown every second
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

// 2. Hash router listener
window.addEventListener('hashchange', () => {
    const p = location.hash.slice(1);
    if (p && p !== current) {
        navigate(p);
    }
});

// 3. Modal backdrop dismiss
$('#dialog')?.addEventListener('click', e => {
    if (e.target === $('#dialog')) $('#dialog').close();
});

// 4. Initial Render
current = location.hash.slice(1) || 'home';
render();

// 5. Initial background fetch for real server data
(async function initServerData() {
    try {
        // Fetch doubts
        const doubtsRes = await fetch(`${ctx}/doubts?json=true`);
        if (doubtsRes.ok) {
            const dbD = await doubtsRes.json();
            if (Array.isArray(dbD) && dbD.length > 0) {
                state.dbDoubts = dbD.map(d => ({
                    id: d.id,
                    subject: d.subject,
                    title: d.title,
                    body: d.body || d.description,
                    author: d.author,
                    resolved: d.resolved,
                    owned: d.owned,
                    replies: d.replies || []
                }));
                save();
                if (current === 'doubts' || current === 'home') render();
            }
        }
    } catch (e) {}

    try {
        // Fetch ghost rooms
        const ghostsRes = await fetch(`${ctx}/ghostrooms?json=true`);
        if (ghostsRes.ok) {
            const dbG = await ghostsRes.json();
            if (Array.isArray(dbG) && dbG.length > 0) {
                state.dbGhosts = dbG.map(g => ({
                    id: String(g.id),
                    title: g.title,
                    topic: g.topic || 'Live temporary study room',
                    subject: g.subject || 'General',
                    expires: g.expires,
                    members: g.members || 4
                }));
                save();
                if (current === 'ghost' || current === 'home') render();
            }
        }
    } catch (e) {}
})();
