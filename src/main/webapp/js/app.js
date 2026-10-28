/**
 * CampusVerse Lite - Frontend Application
 * Fully integrated Single Page Application (SPA) with Java Jakarta EE Backend.
 */

const $ = s => document.querySelector(s);
const $$ = s => document.querySelectorAll(s);
const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
}[c]));

const ctx = window.APP_CONTEXT || '';

// Default application state
const defaults = {
    profile: {
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
    if (!state.profile || !state.profile.name) {
        state.profile = defaults.profile;
    }
} catch {
    state = structuredClone(defaults);
}

function save() {
    try {
        localStorage.setItem('campusverse-v1', JSON.stringify(state));
    } catch (e) {
        console.warn('LocalStorage error:', e);
    }
}

// Navigation & route definitions
const navs = [
    ['home', '▦', 'Overview'],
    ['chat', '▤', 'Study Chat'],
    ['match', '✳', 'VibeMatch'],
    ['ghost', '♧', 'GhostRooms'],
    ['doubts', '◈', 'DoubtDrop']
];

const interests = ['Java', 'DSA', 'UI/UX', 'React', 'Hackathons', 'Gaming', 'DBMS', 'Python'];
const subjects = ['DSA', 'Java', 'DBMS', 'General'];

let current = 'home';
let room = 'DSA';
let ghostId = null;
let filter = 'All';
let search = '';
let lastPolledMessageId = 0;
let roomMap = { 'Java': 1, 'DSA': 2, 'DBMS': 3, 'General': 4 };

// Reference peers for VibeMatch
const peers = [
    {
        name: 'Aman Sharma',
        initials: 'AS',
        color: 'peach',
        interests: ['Java', 'React', 'Hackathons'],
        bio: 'Building things. Breaking things. Learning both ways.',
        year: 'CSE · 2nd year'
    },
    {
        name: 'Riya Verma',
        initials: 'RV',
        color: 'pink',
        interests: ['Java', 'DSA', 'Hackathons'],
        bio: 'One more problem before I call it a day.',
        year: 'CSE · 2nd year'
    },
    {
        name: 'Kabir Singh',
        initials: 'KS',
        color: 'blue',
        interests: ['UI/UX', 'React', 'Gaming'],
        bio: 'Good interfaces and late-night gaming sessions.',
        year: 'CSE · 3rd year'
    },
    {
        name: 'Ananya Das',
        initials: 'AD',
        color: 'lime',
        interests: ['DBMS', 'Python', 'DSA'],
        bio: 'Turning confusing concepts into little diagrams.',
        year: 'CSE · 2nd year'
    }
];

// Seed doubts for initial exploration
const seedDoubts = [
    {
        id: 'seed1',
        subject: 'Java',
        title: 'Can someone explain method overloading with a real-life example?',
        body: 'I understand the syntax, but when should I use it instead of creating a new method?',
        author: 'Riya Verma',
        resolved: false,
        replies: [
            {
                author: 'Aman Sharma',
                text: 'Use overloading when the operation is the same but the input differs — like add(int, int) and add(double, double). Java picks the method from argument types.'
            }
        ]
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
        replies: [
            {
                author: 'Riya Verma',
                text: 'Each comparison halves the search space. After k steps, n / 2^k = 1, so k = log₂(n).'
            }
        ]
    }
];

let baseTime = Number(localStorage.getItem('campusverse-start')) || Date.now();
localStorage.setItem('campusverse-start', baseTime);

function allGhosts() {
    const defaultsGhosts = [
        { id: 'dbms', title: 'DBMS: the last-minute club', topic: 'Normalization, SQL & a little moral support.', subject: 'DBMS', expires: baseTime + 7200000, members: 8 },
        { id: 'algo', title: 'Two pointers, one brain cell', topic: 'Let’s work through the DSA practice sheet.', subject: 'DSA', expires: baseTime + 14400000, members: 5 },
        { id: 'hack', title: 'Hackathon idea jam', topic: 'No bad ideas. Just bring your curiosity.', subject: 'General', expires: baseTime + 28800000, members: 6 }
    ];
    return [...state.ghosts, ...defaultsGhosts];
}

function allDoubts() {
    return [
        ...state.doubts,
        ...seedDoubts.map(d => ({ ...d, ...(state['doubt-' + d.id] || {}) }))
    ];
}

function updateDoubt(d) {
    if (String(d.id).startsWith('seed')) {
        state['doubt-' + d.id] = d;
    } else {
        const idx = state.doubts.findIndex(x => x.id === d.id);
        if (idx >= 0) state.doubts[idx] = d;
        else state.doubts.unshift(d);
    }
    save();
}

function toast(text) {
    const el = $('#toast');
    if (!el) return;
    el.textContent = text;
    el.classList.add('show');
    clearTimeout(window.tt);
    window.tt = setTimeout(() => el.classList.remove('show'), 3000);
}

function initials(nameStr) {
    if (!nameStr) return 'ST';
    const parts = nameStr.trim().split(/\s+/);
    if (parts.length >= 2) {
        return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return nameStr.substring(0, 2).toUpperCase();
}

function name() {
    return state.profile.name || 'Milee Kesarwani';
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

function navigate(page) {
    current = page;
    location.hash = page;
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

    // Breadcrumb and profile avatars
    const crumb = $('#crumb');
    if (crumb) {
        crumb.textContent = navs.find(x => x[0] === current)?.[2] ||
            (current === 'saved' ? 'Your activity' : 'Your profile');
    }

    const userNameEl = $('#user-name');
    if (userNameEl) userNameEl.textContent = name();

    const currentInitials = initials(name());
    const headerAvatar = $('.header-right .avatar') || $('.header-avatar');
    if (headerAvatar) headerAvatar.textContent = currentInitials;

    const profileAvatar = $('.profile-button .avatar');
    if (profileAvatar) profileAvatar.textContent = currentInitials;

    // View router
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
        const msgsEl = $('.messages');
        if (msgsEl) msgsEl.scrollTop = msgsEl.scrollHeight;
        syncRoomMessages();
    }
}

// ----------------------------------------------------
// VIEW: Home (Overview)
// ----------------------------------------------------
function home() {
    const firstName = esc(name().split(' ')[0]);
    const liveGhostCount = allGhosts().filter(g => g.expires > Date.now()).length;
    const openDoubtsCount = allDoubts().filter(d => !d.resolved).length;

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
                <div><b>04</b><small>Study rooms</small></div>
            </div>
            <div class="stat">
                <span class="stat-icon">✳</span>
                <div><b>04</b><small>People to discover</small></div>
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
                    <span>4 subject rooms</span>
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
                    <div class="room-row">
                        <span class="room-icon">⌘</span>
                        <div class="room-info">
                            <b>Data Structures & Algorithms</b>
                            <p><i></i> Binary trees & better understanding</p>
                        </div>
                        <div class="faces">
                            <span class="avatar peach">AS</span>
                            <span class="avatar purple">RV</span>
                            <span class="avatar lime">+12</span>
                        </div>
                        <button class="room-action" onclick="openRoom('DSA')">Join ↗</button>
                    </div>
                    <div class="room-row">
                        <span class="room-icon">☕</span>
                        <div class="room-info">
                            <b>The Java Junction</b>
                            <p><i></i> Classes, coffee & conversations</p>
                        </div>
                        <div class="faces">
                            <span class="avatar pink">RV</span>
                            <span class="avatar blue">KS</span>
                            <span class="avatar lime">+8</span>
                        </div>
                        <button class="room-action" onclick="openRoom('Java')">Join ↗</button>
                    </div>
                    <div class="room-row">
                        <span class="room-icon">▥</span>
                        <div class="room-info">
                            <b>DBMS Study Circle</b>
                            <p><i></i> Making sense of relationships</p>
                        </div>
                        <div class="faces">
                            <span class="avatar lime">AD</span>
                            <span class="avatar peach">AS</span>
                            <span class="avatar purple">+6</span>
                        </div>
                        <button class="room-action" onclick="openRoom('DBMS')">Join ↗</button>
                    </div>
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
        </div>
    `;
}

// ----------------------------------------------------
// VIEW: Study Chat
// ----------------------------------------------------
function openRoom(s, id = null) {
    room = s;
    ghostId = id;
    lastPolledMessageId = 0;
    navigate('chat');
}

function chat() {
    let g = ghostId ? allGhosts().find(x => x.id === ghostId) : null;
    const key = ghostId || room;
    const msgs = state.messages[key] || [];
    let expired = g && g.expires <= Date.now();

    return heading('A good place to get it.', 'Ask a question, share a thought, or just say hello.') + `
        <div class="info-banner">Live Chat · Powered by persistent H2 database & cursor polling API. Messages are saved securely on CampusVerse Lite.</div>
        <div class="workspace">
            <aside class="panel room-list">
                ${subjects.map(s => `
                    <button class="${room === s && !ghostId ? 'active' : ''}" onclick="openRoom('${s}')">
                        # ${s}
                        <small>${s === 'General' ? 'The campus common room' : 'Your ' + s + ' study circle'}</small>
                    </button>
                `).join('')}
                ${g ? `
                    <button class="active">
                        ♧ ${esc(g.title)}
                        <small>Temporary room</small>
                    </button>
                ` : ''}
            </aside>

            <section class="panel chat-panel">
                <div class="chat-top">
                    ${g ? '♧ ' + esc(g.title) : '# ' + room + ' Study Circle'}
                    <small>${expired ? 'This room has expired. You can still read the conversation.' : 'A little curiosity goes a long way. Be kind, always.'}</small>
                </div>

                <div class="messages" id="chat-messages">
                    ${!g && msgs.length === 0 ? `
                        <div class="message">
                            <span class="avatar pink">RV</span>
                            <div>
                                <b>Riya Verma</b><time>Study peer</time>
                                <div class="bubble">
                                    ${room === 'DSA' ? 'Anyone up for working through binary search together? I keep mixing up the boundary conditions.' :
                                      room === 'Java' ? 'Welcome to the Java Junction! What are you working on today?' :
                                      room === 'DBMS' ? 'Revision check-in: joins, normalization, or transactions? Where should we start?' :
                                      'Hey everyone! What’s one thing you learned today?'}
                                </div>
                            </div>
                        </div>
                        <div class="message">
                            <span class="avatar peach">AS</span>
                            <div>
                                <b>Aman Sharma</b><time>Study peer</time>
                                <div class="bubble">
                                    ${room === 'DSA' ? 'I’m in! Start with low = 0 and high = n − 1, and keep going while low ≤ high. Let’s try a small example.' :
                                      'Happy to help! Drop your questions here and we can figure them out together.'}
                                </div>
                            </div>
                        </div>
                    ` : ''}

                    ${msgs.map(m => `
                        <div class="message ${m.author === name() ? 'mine' : ''}">
                            <span class="avatar ${m.author === name() ? 'lime' : 'purple'}">${esc(initials(m.author))}</span>
                            <div>
                                <b>${esc(m.author)}</b><time>${esc(m.time)}</time>
                                <div class="bubble">${esc(m.text)}</div>
                            </div>
                        </div>
                    `).join('')}
                </div>

                <form class="compose" onsubmit="sendMessage(event)">
                    <input name="message" maxlength="2000" aria-label="Message"
                           placeholder="${expired ? 'This GhostRoom is closed' : 'A question? A thought? Drop it here…'}"
                           required ${expired ? 'disabled' : ''} autocomplete="off">
                    <button class="primary" ${expired ? 'disabled' : ''}>Send ↗</button>
                </form>
            </section>
        </div>
    `;
}

async function sendMessage(e) {
    e.preventDefault();
    if (ghostId) {
        const g = allGhosts().find(x => x.id === ghostId);
        if (g && g.expires <= Date.now()) {
            toast('This room has expired.');
            render();
            return;
        }
    }

    const form = e.target;
    const input = form.querySelector('input[name="message"]');
    const text = input ? input.value.trim() : '';
    if (!text) return;

    input.value = '';

    const key = ghostId || room;
    const timeStr = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

    // Local optimistic update
    (state.messages[key] ??= []).push({
        author: name(),
        text: text,
        time: timeStr
    });
    save();
    render();

    // Sync with backend API
    const targetRoomId = ghostId ? Number(ghostId) : (roomMap[room] || 1);
    try {
        const formData = new URLSearchParams();
        formData.append('roomId', targetRoomId);
        formData.append('content', text);

        const res = await fetch(`${ctx}/messages`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'Accept': 'application/json'
            },
            body: formData.toString()
        });

        if (res.ok) {
            const data = await res.json();
            if (data.messageId) {
                lastPolledMessageId = Math.max(lastPolledMessageId, data.messageId);
            }
        }
    } catch (err) {
        console.warn('Backend chat sync error:', err);
    }
}

async function syncRoomMessages() {
    const targetRoomId = ghostId ? Number(ghostId) : (roomMap[room] || 1);
    if (!targetRoomId || isNaN(targetRoomId)) return;

    try {
        const res = await fetch(`${ctx}/api/messages?roomId=${targetRoomId}&afterId=${lastPolledMessageId}`);
        if (!res.ok) return;

        const msgs = await res.json();
        if (Array.isArray(msgs) && msgs.length > 0) {
            const key = ghostId || room;
            state.messages[key] ??= [];

            for (const m of msgs) {
                lastPolledMessageId = Math.max(lastPolledMessageId, m.id);
                // Avoid duplicating our own freshly sent messages
                const exists = state.messages[key].some(
                    local => local.text === m.content && (local.author === m.senderNickname || local.id === m.id)
                );
                if (!exists) {
                    state.messages[key].push({
                        id: m.id,
                        author: m.senderNickname,
                        text: m.content,
                        time: m.createdAt ? m.createdAt.substring(11, 16) : ''
                    });
                }
            }
            save();

            const msgsEl = $('#chat-messages');
            if (msgsEl && current === 'chat') {
                msgsEl.innerHTML = state.messages[key].map(m => `
                    <div class="message ${m.author === name() ? 'mine' : ''}">
                        <span class="avatar ${m.author === name() ? 'lime' : 'purple'}">${esc(initials(m.author))}</span>
                        <div>
                            <b>${esc(m.author)}</b><time>${esc(m.time)}</time>
                            <div class="bubble">${esc(m.text)}</div>
                        </div>
                    </div>
                `).join('');
                msgsEl.scrollTop = msgsEl.scrollHeight;
            }
        }
    } catch (err) {
        // Polling error ignored gracefully
    }
}

// ----------------------------------------------------
// VIEW: VibeMatch
// ----------------------------------------------------
function match() {
    const chosen = state.profile.interests || [];
    const list = peers.map(p => {
        const shared = p.interests.filter(i => chosen.includes(i));
        const union = new Set([...p.interests, ...chosen]);
        const score = union.size === 0 ? 0 : Math.round((shared.length / union.size) * 100);
        return { ...p, score };
    }).sort((a, b) => b.score - a.score);

    return heading('Your people are out there.', 'Start with what you love. Find someone who gets it.') + `
        <div class="info-banner">VibeMatch compares shared interests using Jaccard Similarity (intersection ÷ union). Choose your interests to see recommendations change in real time.</div>

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
                    <h3>${esc(p.name)}</h3>
                    <span class="muted">${esc(p.year)}</span>
                    <p>${esc(p.bio)}</p>
                    <div>
                        ${p.interests.map(i => `
                            <span class="chip ${chosen.includes(i) ? 'green' : ''}">${esc(i)}</span>
                        `).join('')}
                    </div>
                    <button class="primary" onclick="connect('${p.initials}')">
                        ${state.connections.includes(p.initials) ? '✓ Saved to your circle' : 'Add to my circle ↗'}
                    </button>
                </article>
            `).join('')}
        </div>
    `;
}

function toggleInterest(i) {
    const arr = state.profile.interests;
    state.profile.interests = arr.includes(i) ? arr.filter(x => x !== i) : [...arr, i];
    save();
    render();

    // Persist to backend
    syncProfileToBackend();
}

function connect(id) {
    if (state.connections.includes(id)) {
        state.connections = state.connections.filter(x => x !== id);
        toast('Removed from your saved circle');
    } else {
        state.connections.push(id);
        toast('Saved to your circle');
    }
    save();
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

function ghosts() {
    return heading(
        'Good conversations. Limited editions.',
        'A room for right now. Revision, ideas, or whatever’s on your mind.',
        `<button class="primary" onclick="createGhost()">+ Create a room</button>`
    ) + `
        <div class="info-banner">GhostRooms automatically lock when their timer runs out. Messages remain readable, but no new messages can be posted.</div>
        <div class="card-grid">
            ${allGhosts().map(g => `
                <article class="ghost-card">
                    <div class="ghost" aria-hidden="true"></div>
                    <h3>${esc(g.title)}</h3>
                    <p>${esc(g.topic)}</p>
                    <span class="chip">${esc(g.subject)}</span>
                    <div class="countdown" data-expiry="${g.expires}">${countdown(g.expires)}</div>
                    <div class="muted">${g.expires > Date.now() ? 'until this room vanishes' : 'This conversation has ended'}</div>
                    <div style="margin-top:22px">
                        <span class="muted">♧ ${g.members} members</span>
                        <button class="room-action" onclick="openRoom('${g.subject}','${g.id}')">
                            ${g.expires > Date.now() ? 'Step inside ↗' : 'Read chat ↗'}
                        </button>
                    </div>
                </article>
            `).join('')}
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
                    ${subjects.map(s => `<option>${s}</option>`).join('')}
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
    const subj = f.get('subject');
    const hours = Number(f.get('hours')) || 2;
    if (!title || !topic) return;

    const expiresTime = Date.now() + hours * 3600000;
    const newGhost = {
        id: 'g' + Date.now(),
        title,
        topic,
        subject: subj,
        expires: expiresTime,
        members: 1
    };

    state.ghosts.unshift(newGhost);
    save();
    $('#dialog').close();
    render();
    toast('Your GhostRoom is ready.');

    // Sync to backend H2 database
    try {
        const params = new URLSearchParams();
        params.append('name', title);
        params.append('duration', `${hours} hours`);

        const res = await fetch(`${ctx}/ghostrooms`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'Accept': 'application/json'
            },
            body: params.toString()
        });

        if (res.ok) {
            const data = await res.json();
            if (data.roomId) {
                newGhost.id = String(data.roomId);
                save();
            }
        }
    } catch (err) {
        console.warn('GhostRoom backend sync error:', err);
    }
}

// ----------------------------------------------------
// VIEW: DoubtDrop
// ----------------------------------------------------
function doubts() {
    const items = allDoubts().filter(d =>
        (filter === 'All' || (filter === 'Resolved' && d.resolved) || (filter === 'Open' && !d.resolved)) &&
        `${d.title} ${d.body} ${d.subject}`.toLowerCase().includes(search.toLowerCase())
    );

    return heading(
        'Big lightbulb energy.',
        'Every “I don’t get it” deserves an “oh, now I do.”',
        `<button class="primary" onclick="askDoubt()">+ Drop a doubt</button>`
    ) + `
        <div class="doubt-controls">
            <input aria-label="Search doubts" placeholder="Search questions, subjects, or concepts…"
                   value="${esc(search)}" oninput="search=this.value;refreshDoubts()">
            <select aria-label="Filter questions" onchange="filter=this.value;render()">
                ${['All', 'Open', 'Resolved'].map(x => `<option ${x === filter ? 'selected' : ''}>${x}</option>`).join('')}
            </select>
        </div>
        <div id="doubt-list">${doubtList(items)}</div>
    `;
}

function doubtList(items) {
    if (!items.length) {
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
                <button class="room-action" onclick="viewDoubt('${d.id}')">Join the conversation ↗</button>
            </div>
        </article>
    `).join('');
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
            <label>
                Subject
                <select name="subject">${subjects.map(s => `<option>${s}</option>`).join('')}</select>
            </label>
            <label>
                Your question
                <input name="title" maxlength="150" placeholder="What’s on your mind?" required autofocus>
            </label>
            <label>
                A little more context
                <textarea name="body" maxlength="2000" placeholder="What have you tried? Where are you stuck?" required></textarea>
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
    const body = f.get('body').trim();
    if (!title || !body) return;

    const newDoubt = {
        id: 'd' + Date.now(),
        subject: subj,
        title: title,
        body: body,
        author: name(),
        owned: true,
        resolved: false,
        replies: []
    };

    state.doubts.unshift(newDoubt);
    save();
    $('#dialog').close();
    navigate('doubts');
    toast('Your question is out there.');

    // Sync to backend H2 database
    try {
        const params = new URLSearchParams();
        params.append('subject', subj);
        params.append('title', title);
        params.append('description', body);

        const res = await fetch(`${ctx}/doubts`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'Accept': 'application/json'
            },
            body: params.toString()
        });

        if (res.ok) {
            const data = await res.json();
            if (data.id) {
                newDoubt.id = data.id;
                save();
            }
        }
    } catch (err) {
        console.warn('Backend doubt sync error:', err);
    }
}

function viewDoubt(id) {
    const d = allDoubts().find(x => String(x.id) === String(id));
    if (!d) return;

    modal(`
        <span class="chip">${esc(d.subject)}</span>
        <h2 style="margin-top:15px">${esc(d.title)}</h2>
        <p>${esc(d.body)}</p>
        <span class="muted">Asked by ${esc(d.author)} · ${d.resolved ? 'Resolved' : 'Open'}</span>

        <div class="answers">
            ${(d.replies || []).length ? d.replies.map(r => `
                <b style="font-size:11px">${esc(r.author)}</b>
                <p>${esc(r.text)}</p>
            `).join('') : '<p>No replies yet. A little help starts with you.</p>'}
        </div>

        <form class="form-stack" onsubmit="replyDoubt(event,'${d.id}')">
            <label>
                Your reply
                <textarea name="reply" required maxlength="2000" placeholder="Share an explanation or a helpful example…"></textarea>
            </label>
            <button class="primary" type="submit">Post reply ↗</button>
        </form>

        ${d.owned || d.author === name() ? `
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

    const d = allDoubts().find(x => String(x.id) === String(id));
    if (!d) return;

    d.replies ??= [];
    d.replies.push({
        author: name(),
        text: val
    });

    updateDoubt(d);
    $('#dialog').close();
    render();
    viewDoubt(id);
    toast('A little help, delivered.');

    // Sync answer to backend if ID is numeric
    if (!String(id).startsWith('seed') && !String(id).startsWith('d')) {
        try {
            const params = new URLSearchParams();
            params.append('doubtId', id);
            params.append('answerText', val);

            await fetch(`${ctx}/doubts/answer`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'Accept': 'application/json'
                },
                body: params.toString()
            });
        } catch (err) {
            console.warn('Backend reply sync error:', err);
        }
    }
}

async function resolveDoubt(id) {
    const d = allDoubts().find(x => String(x.id) === String(id));
    if (!d) return;

    d.resolved = !d.resolved;
    updateDoubt(d);
    $('#dialog').close();
    render();
    toast(d.resolved ? 'A lightbulb moment! Marked resolved.' : 'Question reopened.');

    // Sync resolve status to backend
    if (!String(id).startsWith('seed') && !String(id).startsWith('d')) {
        try {
            const params = new URLSearchParams();
            params.append('action', 'resolve');
            params.append('doubtId', id);

            await fetch(`${ctx}/doubts`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'Accept': 'application/json'
                },
                body: params.toString()
            });
        } catch (err) {
            console.warn('Backend resolve sync error:', err);
        }
    }
}

// ----------------------------------------------------
// VIEW: Profile
// ----------------------------------------------------
function profile() {
    return heading('A little more you.', 'Your name and interests help your campus find you.') + `
        <section class="panel profile-panel">
            <h2>Your campus identity</h2>
            <p class="muted">Connected directly to CampusVerse Lite backend session and database.</p>
            <form class="form-stack" onsubmit="saveProfile(event)">
                <label>
                    Your nickname
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
                <button class="primary" type="submit">Save my profile ↗</button>
            </form>
        </section>
    `;
}

async function saveProfile(e) {
    e.preventDefault();
    const n = new FormData(e.target).get('nickname').trim();
    if (!n) return;

    state.profile.name = n;
    save();
    render();
    toast('Looking like you. Profile saved.');

    syncProfileToBackend();
}

async function syncProfileToBackend() {
    try {
        const params = new URLSearchParams();
        params.append('action', 'saveProfile');
        params.append('nickname', state.profile.name);
        for (const interest of (state.profile.interests || [])) {
            params.append('interests', interest);
        }

        await fetch(`${ctx}/student`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'Accept': 'application/json'
            },
            body: params.toString()
        });
    } catch (err) {
        console.warn('Profile sync error:', err);
    }
}

// ----------------------------------------------------
// VIEW: Saved Activity
// ----------------------------------------------------
function saved() {
    return heading('Your little corner of campus.', 'The people, questions, and conversations you’ve started.') + `
        <div class="panel">
            <h2>Your circle</h2>
            ${state.connections.length ? peers.filter(p => state.connections.includes(p.initials)).map(p => `
                <div class="room-row">
                    <span class="avatar ${p.color}">${p.initials}</span>
                    <div class="room-info">
                        <b>${esc(p.name)}</b>
                        <p>${p.interests.join(' · ')}</p>
                    </div>
                    <button class="room-action" onclick="navigate('match')">View matches ↗</button>
                </div>
            `).join('') : '<p class="muted">Your circle is waiting. Find someone with a shared interest in VibeMatch.</p>'}

            <h2 style="margin-top:28px">Your questions</h2>
            ${doubtList(state.doubts)}

            <button class="primary" style="margin-top:20px" onclick="navigate('match')">Explore VibeMatch ↗</button>
        </div>
    `;
}

// ----------------------------------------------------
// INITIALIZATION & TIMERS
// ----------------------------------------------------

// Live countdown ticker & active chat polling
setInterval(() => {
    // 1. Update GhostRoom countdown timers
    document.querySelectorAll('[data-expiry]').forEach(el => {
        el.textContent = countdown(Number(el.dataset.expiry));
    });

    // 2. Poll messages if currently on chat screen
    if (current === 'chat') {
        syncRoomMessages();
    }
}, 2000);

// Hash routing
window.addEventListener('hashchange', () => {
    const p = location.hash.slice(1);
    if (p && p !== current) {
        current = p;
        render();
    }
});

// Modal outside-click dismiss
const dialog = $('#dialog');
if (dialog) {
    dialog.addEventListener('click', e => {
        if (e.target === dialog) dialog.close();
    });
}

// Initial Boot
async function boot() {
    // Fetch live study rooms from backend
    try {
        const res = await fetch(`${ctx}/chat?rooms=true`);
        if (res.ok) {
            const roomsData = await res.json();
            if (Array.isArray(roomsData)) {
                for (const r of roomsData) {
                    if (r.name && r.id) {
                        roomMap[r.name] = r.id;
                    }
                }
            }
        }
    } catch (ignored) {}

    // Fetch existing doubts from backend
    try {
        const res = await fetch(`${ctx}/doubts?json=true`);
        if (res.ok) {
            const doubtsData = await res.json();
            if (Array.isArray(doubtsData) && doubtsData.length > 0) {
                // Merge with state.doubts
                for (const d of doubtsData) {
                    const exists = state.doubts.some(x => x.id === d.id);
                    if (!exists) {
                        state.doubts.push({
                            id: d.id,
                            subject: d.subject,
                            title: d.title,
                            body: d.body,
                            author: d.author,
                            owned: d.owned,
                            resolved: d.resolved,
                            replies: (d.replies || []).map(r => ({ author: r.author, text: r.text }))
                        });
                    }
                }
                save();
            }
        }
    } catch (ignored) {}

    // Set initial route and render
    current = location.hash.slice(1) || 'home';
    render();
}

boot();

