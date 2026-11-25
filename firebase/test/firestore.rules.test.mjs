import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, it } from 'node:test';

import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  collection,
  deleteDoc,
  deleteField,
  doc,
  getDoc,
  getDocs,
  serverTimestamp,
  setDoc,
  Timestamp,
  updateDoc,
} from 'firebase/firestore';

// ADR-010: users/{uid} is owner-only, writes are validated, and everything else is denied.
// ADR-011: users/{uid}/settings/preferences holds synced preferences as { value, updatedAt } fields.
// The emulator is started by `npm test` (firebase emulators:exec), which sets FIRESTORE_EMULATOR_HOST.

const ALICE = 'alice';
const BOB = 'bob';
const SEEDED_AT = Timestamp.fromDate(new Date('2026-01-01T00:00:00Z'));

let testEnv;

const firestoreAs = (uid) =>
  uid ? testEnv.authenticatedContext(uid).firestore() : testEnv.unauthenticatedContext().firestore();

const userDoc = (db, uid) => doc(db, 'users', uid);

async function seedUser(uid, data = { createdAt: SEEDED_AT, displayName: 'Seeded' }) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(userDoc(context.firestore(), uid), data);
  });
}

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'demo-rolabox',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

after(async () => {
  await testEnv?.cleanup();
});

describe('users/{uid} access', () => {
  it('lets the owner read their own document', async () => {
    await seedUser(ALICE);
    await assertSucceeds(getDoc(userDoc(firestoreAs(ALICE), ALICE)));
  });

  it("denies reading another user's document", async () => {
    await seedUser(BOB);
    await assertFails(getDoc(userDoc(firestoreAs(ALICE), BOB)));
  });

  it('denies reading when signed out', async () => {
    await seedUser(ALICE);
    await assertFails(getDoc(userDoc(firestoreAs(null), ALICE)));
  });

  it('denies listing the users collection', async () => {
    await seedUser(ALICE);
    await assertFails(getDocs(collection(firestoreAs(ALICE), 'users')));
  });

  it("denies creating another user's document", async () => {
    await assertFails(setDoc(userDoc(firestoreAs(ALICE), BOB), { createdAt: serverTimestamp() }));
  });

  it('denies creating a document when signed out', async () => {
    await assertFails(setDoc(userDoc(firestoreAs(null), ALICE), { createdAt: serverTimestamp() }));
  });

  it("denies updating another user's document", async () => {
    await seedUser(BOB);
    await assertFails(updateDoc(userDoc(firestoreAs(ALICE), BOB), { displayName: 'Mallory' }));
  });

  it('lets the owner delete their own document', async () => {
    await seedUser(ALICE);
    await assertSucceeds(deleteDoc(userDoc(firestoreAs(ALICE), ALICE)));
  });

  it("denies deleting another user's document", async () => {
    await seedUser(BOB);
    await assertFails(deleteDoc(userDoc(firestoreAs(ALICE), BOB)));
  });
});

describe('users/{uid} create validation', () => {
  it('accepts createdAt as the server time', async () => {
    await assertSucceeds(setDoc(userDoc(firestoreAs(ALICE), ALICE), { createdAt: serverTimestamp() }));
  });

  it('accepts a displayName of 1 to 100 characters', async () => {
    const db = firestoreAs(ALICE);
    await assertSucceeds(setDoc(userDoc(db, ALICE), { createdAt: serverTimestamp(), displayName: 'A' }));
    await testEnv.clearFirestore();
    await assertSucceeds(
      setDoc(userDoc(db, ALICE), { createdAt: serverTimestamp(), displayName: 'a'.repeat(100) }),
    );
  });

  it('rejects a missing createdAt', async () => {
    await assertFails(setDoc(userDoc(firestoreAs(ALICE), ALICE), { displayName: 'Alice' }));
  });

  it('rejects a createdAt set by the client', async () => {
    await assertFails(setDoc(userDoc(firestoreAs(ALICE), ALICE), { createdAt: SEEDED_AT }));
  });

  it('rejects unknown fields', async () => {
    await assertFails(
      setDoc(userDoc(firestoreAs(ALICE), ALICE), { createdAt: serverTimestamp(), isAdmin: true }),
    );
  });

  it('rejects a displayName that is not a string', async () => {
    await assertFails(
      setDoc(userDoc(firestoreAs(ALICE), ALICE), { createdAt: serverTimestamp(), displayName: 42 }),
    );
  });

  it('rejects an empty displayName', async () => {
    await assertFails(
      setDoc(userDoc(firestoreAs(ALICE), ALICE), { createdAt: serverTimestamp(), displayName: '' }),
    );
  });

  it('rejects a displayName over 100 characters', async () => {
    await assertFails(
      setDoc(userDoc(firestoreAs(ALICE), ALICE), {
        createdAt: serverTimestamp(),
        displayName: 'a'.repeat(101),
      }),
    );
  });
});

describe('users/{uid} update validation', () => {
  beforeEach(async () => {
    await seedUser(ALICE);
  });

  it('accepts changing displayName', async () => {
    await assertSucceeds(updateDoc(userDoc(firestoreAs(ALICE), ALICE), { displayName: 'Alice' }));
  });

  it('rejects changing createdAt', async () => {
    await assertFails(updateDoc(userDoc(firestoreAs(ALICE), ALICE), { createdAt: serverTimestamp() }));
  });

  it('rejects adding an unknown field', async () => {
    await assertFails(updateDoc(userDoc(firestoreAs(ALICE), ALICE), { isAdmin: true }));
  });

  it('rejects a displayName over 100 characters', async () => {
    await assertFails(updateDoc(userDoc(firestoreAs(ALICE), ALICE), { displayName: 'a'.repeat(101) }));
  });
});

const preferencesDoc = (db, uid) => doc(db, 'users', uid, 'settings', 'preferences');

const synced = (value, date) => ({ value, updatedAt: Timestamp.fromDate(date) });
const hoursFromNow = (hours) => new Date(Date.now() + hours * 60 * 60 * 1000);
const OLDER = new Date('2026-01-01T00:00:00Z');
const NEWER = new Date('2026-02-01T00:00:00Z');

async function seedPreferences(uid, data) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(preferencesDoc(context.firestore(), uid), data);
  });
}

describe('users/{uid}/settings/preferences access', () => {
  it('lets the owner read and write their preferences', async () => {
    const db = firestoreAs(ALICE);
    await assertSucceeds(setDoc(preferencesDoc(db, ALICE), { darkThemeConfig: synced('DARK', OLDER) }));
    await assertSucceeds(getDoc(preferencesDoc(db, ALICE)));
  });

  it("denies reading another user's preferences", async () => {
    await seedPreferences(BOB, { darkThemeConfig: synced('DARK', OLDER) });
    await assertFails(getDoc(preferencesDoc(firestoreAs(ALICE), BOB)));
  });

  it("denies writing another user's preferences", async () => {
    await assertFails(setDoc(preferencesDoc(firestoreAs(ALICE), BOB), { darkThemeConfig: synced('DARK', OLDER) }));
  });

  it('denies reading and writing when signed out', async () => {
    await seedPreferences(ALICE, { darkThemeConfig: synced('DARK', OLDER) });
    const db = firestoreAs(null);
    await assertFails(getDoc(preferencesDoc(db, ALICE)));
    await assertFails(setDoc(preferencesDoc(db, ALICE), { darkThemeConfig: synced('LIGHT', NEWER) }));
  });

  // ADR-037: deleting the account deletes the preferences. Sync itself never deletes them.
  it('lets the owner delete their preferences', async () => {
    await seedPreferences(ALICE, { darkThemeConfig: synced('DARK', OLDER) });
    await assertSucceeds(deleteDoc(preferencesDoc(firestoreAs(ALICE), ALICE)));
  });

  it("denies deleting another user's preferences", async () => {
    await seedPreferences(BOB, { darkThemeConfig: synced('DARK', OLDER) });
    await assertFails(deleteDoc(preferencesDoc(firestoreAs(ALICE), BOB)));
  });

  it('denies deleting the preferences when signed out', async () => {
    await seedPreferences(ALICE, { darkThemeConfig: synced('DARK', OLDER) });
    await assertFails(deleteDoc(preferencesDoc(firestoreAs(null), ALICE)));
  });

  it('denies other documents in the settings collection', async () => {
    const other = doc(firestoreAs(ALICE), 'users', ALICE, 'settings', 'other');
    await assertFails(setDoc(other, { darkThemeConfig: synced('DARK', OLDER) }));
    await assertFails(getDoc(other));
  });
});

describe('users/{uid}/settings/preferences validation', () => {
  const write = (data) => setDoc(preferencesDoc(firestoreAs(ALICE), ALICE), data);

  it('accepts every known value of each field', async () => {
    for (const value of ['FOLLOW_SYSTEM', 'LIGHT', 'DARK']) {
      await testEnv.clearFirestore();
      await assertSucceeds(write({ darkThemeConfig: synced(value, OLDER) }));
    }
    for (const value of ['BLUE', 'GREEN', 'PURPLE', 'PINK']) {
      await testEnv.clearFirestore();
      await assertSucceeds(write({ accentColor: synced(value, OLDER) }));
    }
  });

  it('accepts both fields together', async () => {
    await assertSucceeds(write({ darkThemeConfig: synced('DARK', OLDER), accentColor: synced('PINK', OLDER) }));
  });

  it('rejects an unknown value', async () => {
    await assertFails(write({ darkThemeConfig: synced('SEPIA', OLDER) }));
    await assertFails(write({ accentColor: synced('ORANGE', OLDER) }));
  });

  it('rejects unknown fields', async () => {
    await assertFails(write({ volume: synced('LOUD', OLDER) }));
  });

  it('rejects a field that is not a { value, updatedAt } map', async () => {
    await assertFails(write({ darkThemeConfig: 'DARK' }));
    await assertFails(write({ darkThemeConfig: { value: 'DARK' } }));
    await assertFails(write({ darkThemeConfig: { updatedAt: Timestamp.fromDate(OLDER) } }));
    await assertFails(write({ darkThemeConfig: { ...synced('DARK', OLDER), by: 'phone' } }));
  });

  it('rejects an updatedAt that is not a timestamp', async () => {
    await assertFails(write({ darkThemeConfig: { value: 'DARK', updatedAt: 1767225600000 } }));
  });

  it('accepts an updatedAt a little ahead of the server clock', async () => {
    await assertSucceeds(write({ darkThemeConfig: synced('DARK', hoursFromNow(1)) }));
  });

  it('rejects an updatedAt more than a day ahead of the server clock', async () => {
    await assertFails(write({ darkThemeConfig: synced('DARK', hoursFromNow(25)) }));
  });
});

describe('users/{uid}/settings/preferences last-write-wins', () => {
  beforeEach(async () => {
    await seedPreferences(ALICE, { darkThemeConfig: synced('DARK', NEWER), accentColor: synced('PINK', OLDER) });
  });

  const update = (data) => setDoc(preferencesDoc(firestoreAs(ALICE), ALICE), data, { merge: true });

  it('accepts a newer value', async () => {
    await assertSucceeds(update({ accentColor: synced('GREEN', NEWER) }));
  });

  it('accepts rewriting a field with the same timestamp', async () => {
    await assertSucceeds(update({ darkThemeConfig: synced('DARK', NEWER) }));
  });

  it('rejects moving a field back in time', async () => {
    await assertFails(update({ darkThemeConfig: synced('LIGHT', OLDER) }));
  });

  it('rejects removing a field', async () => {
    await assertFails(setDoc(preferencesDoc(firestoreAs(ALICE), ALICE), { accentColor: synced('GREEN', NEWER) }));
  });
});

// The device's settings (ADR-011, revised 2026-10-12, and ADR-028): each field is checked like the theme.
const DEVICE_SETTINGS = {
  clicker: { values: ['ON', 'OFF'], unknown: 'LOUD' },
  shuffle: { values: ['OFF', 'SONGS', 'ALBUMS'], unknown: 'ARTISTS' },
  repeat: { values: ['OFF', 'ONE', 'ALL'], unknown: 'TWICE' },
  mainMenuMusic: { values: ['SHOWN', 'HIDDEN'], unknown: 'GONE' },
  mainMenuPodcasts: { values: ['SHOWN', 'HIDDEN'], unknown: 'GONE' },
  mainMenuAudiobooks: { values: ['SHOWN', 'HIDDEN'], unknown: 'GONE' },
  mainMenuShuffleSongs: { values: ['SHOWN', 'HIDDEN'], unknown: 'GONE' },
};

for (const [field, { values, unknown }] of Object.entries(DEVICE_SETTINGS)) {
  describe(`users/{uid}/settings/preferences ${field}`, () => {
    const write = (data) => setDoc(preferencesDoc(firestoreAs(ALICE), ALICE), data);
    const update = (data) => setDoc(preferencesDoc(firestoreAs(ALICE), ALICE), data, { merge: true });

    it('accepts every allowed value', async () => {
      for (const value of values) {
        await testEnv.clearFirestore();
        await assertSucceeds(write({ [field]: synced(value, OLDER) }));
      }
    });

    it('rejects an unknown value', async () => {
      await assertFails(write({ [field]: synced(unknown, OLDER) }));
    });

    it('rejects an updatedAt more than a day ahead of the server clock', async () => {
      await assertFails(write({ [field]: synced(values[0], hoursFromNow(25)) }));
    });

    it('accepts a newer value, and rejects moving it back in time', async () => {
      await seedPreferences(ALICE, { [field]: synced(values[0], OLDER) });
      await assertSucceeds(update({ [field]: synced(values[1], NEWER) }));
      await assertFails(update({ [field]: synced(values[0], OLDER) }));
    });

    it('rejects removing it', async () => {
      await seedPreferences(ALICE, { [field]: synced(values[0], OLDER), darkThemeConfig: synced('DARK', OLDER) });
      await assertFails(write({ darkThemeConfig: synced('DARK', NEWER) }));
    });
  });
}

describe('users/{uid}/settings/preferences with every field', () => {
  it('accepts all the fields together, and still rejects an unknown one', async () => {
    const all = { darkThemeConfig: synced('DARK', OLDER), accentColor: synced('PINK', OLDER) };
    for (const [field, { values }] of Object.entries(DEVICE_SETTINGS)) all[field] = synced(values[1], OLDER);
    const db = firestoreAs(ALICE);
    await assertSucceeds(setDoc(preferencesDoc(db, ALICE), all));
    await assertFails(setDoc(preferencesDoc(db, ALICE), { ...all, mainMenuSettings: synced('HIDDEN', NEWER) }));
  });
});

// ADR-031 (Syncing follows, rule 14): users/{uid}/podcastFollows/{showKey}, one document per show.
const DAILY = 'itunes-1200361736';
const followDoc = (db, uid, showKey = DAILY) => doc(db, 'users', uid, 'podcastFollows', showKey);
const follow = (followed, date, feedUrl = 'https://feeds.example.com/daily') => ({
  feedUrl,
  followed,
  updatedAt: Timestamp.fromDate(date),
});

async function seedFollow(uid, data, showKey = DAILY) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(followDoc(context.firestore(), uid, showKey), data);
  });
}

describe('users/{uid}/podcastFollows access', () => {
  it('lets the owner write, read and list their follows', async () => {
    const db = firestoreAs(ALICE);
    await assertSucceeds(setDoc(followDoc(db, ALICE), follow(true, OLDER)));
    await assertSucceeds(getDoc(followDoc(db, ALICE)));
    await assertSucceeds(getDocs(collection(db, 'users', ALICE, 'podcastFollows')));
  });

  it("denies reading and listing another user's follows", async () => {
    await seedFollow(BOB, follow(true, OLDER));
    const db = firestoreAs(ALICE);
    await assertFails(getDoc(followDoc(db, BOB)));
    await assertFails(getDocs(collection(db, 'users', BOB, 'podcastFollows')));
  });

  it("denies writing another user's follows", async () => {
    await assertFails(setDoc(followDoc(firestoreAs(ALICE), BOB), follow(true, OLDER)));
  });

  it('denies reading and writing when signed out', async () => {
    await seedFollow(ALICE, follow(true, OLDER));
    const db = firestoreAs(null);
    await assertFails(getDoc(followDoc(db, ALICE)));
    await assertFails(setDoc(followDoc(db, ALICE), follow(false, NEWER)));
  });

  // ADR-037: deleting the account deletes every follow. Sync itself never deletes one: an unfollow is kept as
  // followed: false (ADR-031).
  it('lets the owner delete their follows', async () => {
    await seedFollow(ALICE, follow(true, OLDER));
    await assertSucceeds(deleteDoc(followDoc(firestoreAs(ALICE), ALICE)));
  });

  it("denies deleting another user's follows", async () => {
    await seedFollow(BOB, follow(true, OLDER));
    await assertFails(deleteDoc(followDoc(firestoreAs(ALICE), BOB)));
  });

  it('denies deleting a follow when signed out', async () => {
    await seedFollow(ALICE, follow(true, OLDER));
    await assertFails(deleteDoc(followDoc(firestoreAs(null), ALICE)));
  });
});

describe('users/{uid}/podcastFollows validation', () => {
  const write = (data, showKey = DAILY) => setDoc(followDoc(firestoreAs(ALICE), ALICE, showKey), data);

  it('accepts a follow and an unfollow', async () => {
    await assertSucceeds(write(follow(true, OLDER)));
    await assertSucceeds(write(follow(false, OLDER), 'itunes-1'));
  });

  it('accepts only a show key of itunes- and digits', async () => {
    await assertFails(write(follow(true, OLDER), 'itunes-'));
    await assertFails(write(follow(true, OLDER), 'itunes-12a'));
    await assertFails(write(follow(true, OLDER), 'podcastindex-12'));
    await assertFails(write(follow(true, OLDER), '1200361736'));
  });

  it('rejects unknown fields, and a missing one', async () => {
    await assertFails(write({ ...follow(true, OLDER), title: 'The Daily' }));
    const { feedUrl, ...withoutFeed } = follow(true, OLDER);
    await assertFails(write(withoutFeed));
    const { followed, ...withoutFollowed } = follow(true, OLDER);
    await assertFails(write(withoutFollowed));
    const { updatedAt, ...withoutUpdatedAt } = follow(true, OLDER);
    await assertFails(write(withoutUpdatedAt));
  });

  it('accepts an http or https feed URL of up to 2,048 characters', async () => {
    await assertSucceeds(write(follow(true, OLDER, 'http://feeds.example.com/daily')));
    const longest = 'https://example.com/' + 'a'.repeat(2048 - 'https://example.com/'.length);
    await assertSucceeds(write(follow(true, OLDER, longest), 'itunes-1'));
  });

  it('rejects a feed URL that is too long, not http(s), or not a string', async () => {
    const tooLong = 'https://example.com/' + 'a'.repeat(2049 - 'https://example.com/'.length);
    await assertFails(write(follow(true, OLDER, tooLong)));
    await assertFails(write(follow(true, OLDER, 'ftp://feeds.example.com/daily')));
    await assertFails(write(follow(true, OLDER, 'feeds.example.com/daily')));
    await assertFails(write(follow(true, OLDER, 'https://')));
    await assertFails(write({ ...follow(true, OLDER), feedUrl: 42 }));
  });

  it('rejects followed that is not a boolean', async () => {
    await assertFails(write({ ...follow(true, OLDER), followed: 'true' }));
    await assertFails(write({ ...follow(true, OLDER), followed: 1 }));
  });

  it('rejects an updatedAt that is not a timestamp', async () => {
    await assertFails(write({ ...follow(true, OLDER), updatedAt: 1767225600000 }));
  });

  it('accepts an updatedAt a little ahead of the server clock', async () => {
    await assertSucceeds(write(follow(true, hoursFromNow(1))));
  });

  it('rejects an updatedAt more than a day ahead of the server clock', async () => {
    await assertFails(write(follow(true, hoursFromNow(25))));
  });
});

describe('users/{uid}/podcastFollows last-write-wins', () => {
  beforeEach(async () => {
    await seedFollow(ALICE, follow(true, NEWER));
  });

  const update = (data) => setDoc(followDoc(firestoreAs(ALICE), ALICE), data);

  it('accepts a newer unfollow, and a newer follow after it', async () => {
    const later = hoursFromNow(1);
    await assertSucceeds(update(follow(false, later)));
    await assertSucceeds(update(follow(true, hoursFromNow(2))));
  });

  it('accepts rewriting the follow with the same timestamp', async () => {
    await assertSucceeds(update(follow(true, NEWER, 'https://feeds.example.com/moved')));
  });

  it('rejects moving the follow back in time', async () => {
    await assertFails(update(follow(false, OLDER)));
  });

  it('rejects removing a field', async () => {
    const { feedUrl, ...withoutFeed } = follow(false, hoursFromNow(1));
    await assertFails(update(withoutFeed));
    await assertFails(updateDoc(followDoc(firestoreAs(ALICE), ALICE), { feedUrl: deleteField() }));
  });
});

// ADR-043 (Syncing the choice, rule 4): users/{uid}/podcastEpisodeOrders/{showKey}, one document per show.
const orderDoc = (db, uid, showKey = DAILY) => doc(db, 'users', uid, 'podcastEpisodeOrders', showKey);
const order = (value, date) => ({ value, updatedAt: Timestamp.fromDate(date) });

async function seedOrder(uid, data, showKey = DAILY) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(orderDoc(context.firestore(), uid, showKey), data);
  });
}

describe('users/{uid}/podcastEpisodeOrders access', () => {
  it('lets the owner write, read and list their choices', async () => {
    const db = firestoreAs(ALICE);
    await assertSucceeds(setDoc(orderDoc(db, ALICE), order('Oldest', OLDER)));
    await assertSucceeds(getDoc(orderDoc(db, ALICE)));
    await assertSucceeds(getDocs(collection(db, 'users', ALICE, 'podcastEpisodeOrders')));
  });

  it("denies reading and listing another user's choices", async () => {
    await seedOrder(BOB, order('Oldest', OLDER));
    const db = firestoreAs(ALICE);
    await assertFails(getDoc(orderDoc(db, BOB)));
    await assertFails(getDocs(collection(db, 'users', BOB, 'podcastEpisodeOrders')));
  });

  it("denies writing another user's choices", async () => {
    await assertFails(setDoc(orderDoc(firestoreAs(ALICE), BOB), order('Oldest', OLDER)));
  });

  it('denies reading and writing when signed out', async () => {
    await seedOrder(ALICE, order('Oldest', OLDER));
    const db = firestoreAs(null);
    await assertFails(getDoc(orderDoc(db, ALICE)));
    await assertFails(setDoc(orderDoc(db, ALICE), order('Newest', NEWER)));
  });

  // ADR-037: deleting the account deletes every choice. Sync itself never deletes one: a cleared choice is kept as
  // Feed (ADR-043).
  it('lets the owner delete their choices', async () => {
    await seedOrder(ALICE, order('Oldest', OLDER));
    await assertSucceeds(deleteDoc(orderDoc(firestoreAs(ALICE), ALICE)));
  });

  it("denies deleting another user's choices", async () => {
    await seedOrder(BOB, order('Oldest', OLDER));
    await assertFails(deleteDoc(orderDoc(firestoreAs(ALICE), BOB)));
  });

  it('denies deleting a choice when signed out', async () => {
    await seedOrder(ALICE, order('Oldest', OLDER));
    await assertFails(deleteDoc(orderDoc(firestoreAs(null), ALICE)));
  });
});

describe('users/{uid}/podcastEpisodeOrders validation', () => {
  const write = (data, showKey = DAILY) => setDoc(orderDoc(firestoreAs(ALICE), ALICE, showKey), data);

  it('accepts each of Newest, Oldest and Feed', async () => {
    await assertSucceeds(write(order('Newest', OLDER)));
    await assertSucceeds(write(order('Oldest', OLDER), 'itunes-1'));
    await assertSucceeds(write(order('Feed', OLDER), 'itunes-2'));
  });

  it('rejects any other value, and a value that is not a string', async () => {
    await assertFails(write(order('Auto', OLDER)));
    await assertFails(write(order('oldest', OLDER)));
    await assertFails(write(order('', OLDER)));
    await assertFails(write({ ...order('Oldest', OLDER), value: 1 }));
  });

  it('accepts only a show key of itunes- and digits', async () => {
    await assertFails(write(order('Oldest', OLDER), 'itunes-'));
    await assertFails(write(order('Oldest', OLDER), 'itunes-12a'));
    await assertFails(write(order('Oldest', OLDER), 'podcastindex-12'));
    await assertFails(write(order('Oldest', OLDER), '1200361736'));
  });

  it('rejects unknown fields, and a missing one', async () => {
    await assertFails(write({ ...order('Oldest', OLDER), followed: true }));
    const { value, ...withoutValue } = order('Oldest', OLDER);
    await assertFails(write(withoutValue));
    const { updatedAt, ...withoutUpdatedAt } = order('Oldest', OLDER);
    await assertFails(write(withoutUpdatedAt));
  });

  it('rejects an updatedAt that is not a timestamp', async () => {
    await assertFails(write({ ...order('Oldest', OLDER), updatedAt: 1767225600000 }));
  });

  it('accepts an updatedAt a little ahead of the server clock', async () => {
    await assertSucceeds(write(order('Oldest', hoursFromNow(1))));
  });

  it('rejects an updatedAt more than a day ahead of the server clock', async () => {
    await assertFails(write(order('Oldest', hoursFromNow(25))));
  });
});

describe('users/{uid}/podcastEpisodeOrders last-write-wins', () => {
  beforeEach(async () => {
    await seedOrder(ALICE, order('Oldest', NEWER));
  });

  const update = (data) => setDoc(orderDoc(firestoreAs(ALICE), ALICE), data);

  it('accepts a newer choice, and a newer clear after it', async () => {
    await assertSucceeds(update(order('Newest', hoursFromNow(1))));
    await assertSucceeds(update(order('Feed', hoursFromNow(2))));
  });

  it('accepts rewriting the choice with the same timestamp', async () => {
    await assertSucceeds(update(order('Oldest', NEWER)));
  });

  it('rejects moving the choice back in time', async () => {
    await assertFails(update(order('Newest', OLDER)));
  });

  it('rejects removing a field', async () => {
    await assertFails(update({ value: 'Newest' }));
    await assertFails(updateDoc(orderDoc(firestoreAs(ALICE), ALICE), { updatedAt: deleteField() }));
  });
});

// The follow document is unchanged by the choices: a phone that doesn't know them keeps writing it as before.
describe('users/{uid}/podcastFollows without the choice', () => {
  it('rejects a choice written into the follow document', async () => {
    const db = firestoreAs(ALICE);
    await assertFails(setDoc(followDoc(db, ALICE), { ...follow(true, OLDER), order: order('Oldest', OLDER) }));
  });
});

describe('deny by default', () => {
  it('denies subcollections under the owner document', async () => {
    const db = firestoreAs(ALICE);
    const playlist = doc(db, 'users', ALICE, 'playlists', 'p1');
    await assertFails(setDoc(playlist, { name: 'Mine' }));
    await assertFails(getDoc(playlist));
  });

  it('denies top-level collections', async () => {
    const db = firestoreAs(ALICE);
    await assertFails(setDoc(doc(db, 'anything', 'x'), { a: 1 }));
    await assertFails(getDoc(doc(db, 'anything', 'x')));
  });
});
