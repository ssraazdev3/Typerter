const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-keystrok-ad125cc1-6226-41ac-b768-e7361c2e4907";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validNotePayload(uid, overrides = {}) {
  const past = new Date(Date.now() - 2000);
  return {
    userId: uid,
    title: "Midnight Draft",
    content: "Once upon a time there was a...",
    wordCount: 7,
    charCount: 30,
    wordGoal: 3000,
    fontId: "special_elite",
    pinned: false,
    archived: false,
    createdAt: past,
    updatedAt: past,
    ...overrides,
  };
}

function validSessionPayload(uid, overrides = {}) {
  return {
    userId: uid,
    noteId: "note_1",
    wordsWritten: 120,
    durationSeconds: 180,
    wpm: 40,
    createdAt: new Date(Date.now() - 2000),
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or list notes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/notes`).get());
  await assertFails(unauthDb.doc(`users/${ALICE_UID}/notes/note_1`).get());
});

test("Unauthenticated user cannot create notes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.doc(`users/${ALICE_UID}/notes/note_1`).set(validNotePayload(ALICE_UID))
  );
});

test("Authenticated owner can create, read, list, update, and delete own note", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const noteRef = aliceDb.doc(`users/${ALICE_UID}/notes/note_1`);

  await assertSucceeds(noteRef.set(validNotePayload(ALICE_UID)));
  await assertSucceeds(noteRef.get());
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/notes`).get());

  await assertSucceeds(
    noteRef.update({
      content: "Updated manuscript line.",
      wordCount: 3,
      charCount: 24,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(noteRef.delete());
});

test("Cross-user read, list, create, update, delete are denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().doc(`users/${ALICE_UID}/notes/note_1`).set(validNotePayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.doc(`users/${ALICE_UID}/notes/note_1`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/notes`).get());
  await assertFails(
    bobDb.doc(`users/${ALICE_UID}/notes/note_2`).set(validNotePayload(BOB_UID))
  );
  await assertFails(
    bobDb.doc(`users/${ALICE_UID}/notes/note_1`).update({ title: "Hijacked" })
  );
  await assertFails(bobDb.doc(`users/${ALICE_UID}/notes/note_1`).delete());
});

test("Rejects shadow/ghost fields and invalid fontId", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/notes/note_ghost`).set(
      validNotePayload(ALICE_UID, { isAdmin: true })
    )
  );
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/notes/note_font`).set(
      validNotePayload(ALICE_UID, { fontId: "comic_sans" })
    )
  );
});

test("Rejects immutable field tampering on update (userId or createdAt)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const noteRef = aliceDb.doc(`users/${ALICE_UID}/notes/note_1`);
  await assertSucceeds(noteRef.set(validNotePayload(ALICE_UID)));

  await assertFails(noteRef.update({ userId: BOB_UID }));
  await assertFails(noteRef.update({ createdAt: new Date(Date.now() - 100) }));
});

test("Rejects invalid document IDs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/notes/bad id with spaces`).set(validNotePayload(ALICE_UID))
  );
});

test("Writing session rules allow owner create/list and forbid update and cross-user access", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const sessionRef = aliceDb.doc(`users/${ALICE_UID}/writing_sessions/sess_1`);

  await assertSucceeds(sessionRef.set(validSessionPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/writing_sessions`).get());
  await assertFails(sessionRef.update({ wpm: 99 }));

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection(`users/${ALICE_UID}/writing_sessions`).get());
  await assertFails(bobDb.doc(`users/${ALICE_UID}/writing_sessions/sess_2`).set(validSessionPayload(ALICE_UID)));
});
