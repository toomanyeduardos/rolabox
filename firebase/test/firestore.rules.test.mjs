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

  it('denies deleting the preferences', async () => {
    await seedPreferences(ALICE, { darkThemeConfig: synced('DARK', OLDER) });
    await assertFails(deleteDoc(preferencesDoc(firestoreAs(ALICE), ALICE)));
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
