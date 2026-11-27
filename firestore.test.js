const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const TODAY_KEY = "2026-11-25";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

function validProfilePayload(uid, ts) {
  return {
    userId: uid,
    displayName: "Maya",
    calorieGoal: 2000,
    proteinGoal: 120,
    carbsGoal: 220,
    fatGoal: 65,
    waterGoal: 8,
    starterSeeded: false,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validMealPayload(uid, ts, extra = {}) {
  return {
    userId: uid,
    dateKey: TODAY_KEY,
    category: "BREAKFAST",
    name: "Oats and banana",
    detail: "Rolled oats with sliced banana and chia",
    calories: 320,
    proteinGrams: 12,
    carbsGrams: 54,
    fatGrams: 7,
    source: "PRESET",
    createdAt: ts,
    updatedAt: ts,
    ...extra,
  };
}

function validHydrationPayload(uid, dateKey, glasses, ts) {
  return {
    userId: uid,
    dateKey,
    glasses,
    createdAt: ts,
    updatedAt: ts,
  };
}

test("Unauthenticated user: denied all reads and writes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, now))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("meals").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("hydration")
      .doc(TODAY_KEY)
      .set(validHydrationPayload(ALICE_UID, TODAY_KEY, 3, now))
  );
});

test("UserProfile: owner create, partial goal update, starterSeeded update, and shadow-field rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const updated = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, created))
  );

  // Partial update: goals + displayName
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Maya Lin",
      calorieGoal: 2150,
      proteinGoal: 135,
      carbsGoal: 230,
      fatGoal: 70,
      waterGoal: 9,
      updatedAt: updated,
    })
  );

  // Partial update: mark starterSeeded = true
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      starterSeeded: true,
      updatedAt: updated,
    })
  );

  // Reject ghost field (shadow update)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: updated,
    })
  );

  // Reject mutating immutable createdAt
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: updated,
      updatedAt: updated,
    })
  );

  // Reject out-of-range calorieGoal
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      calorieGoal: 200,
      updatedAt: updated,
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Future",
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("MealEntry: owner create (with and without optional insight), filtered list, update, delete, and strict bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const updated = new Date(Date.now() - 1000);
  const mealsRef = aliceDb.collection("users").doc(ALICE_UID).collection("meals");

  // Empty subcollection list query matching Kotlin repository (.whereEqualTo("dateKey", dateKey))
  await assertSucceeds(mealsRef.where("dateKey", "==", TODAY_KEY).get());

  // Create without optional insight (Kotlin omits null optional fields)
  await assertSucceeds(mealsRef.doc("meal_1").set(validMealPayload(ALICE_UID, created)));

  // Create AI_SCAN meal with optional insight
  await assertSucceeds(
    mealsRef.doc("meal_2").set(
      validMealPayload(ALICE_UID, created, {
        category: "LUNCH",
        name: "Avocado grain bowl",
        detail: "Quinoa, roasted chickpeas, avocado",
        calories: 540,
        proteinGrams: 22,
        carbsGrams: 58,
        fatGrams: 24,
        source: "AI_SCAN",
        insight: "Balanced fiber and healthy monounsaturated fats.",
      })
    )
  );

  // Filtered query succeeds
  await assertSucceeds(mealsRef.where("dateKey", "==", TODAY_KEY).get());

  // Partial update succeeds
  await assertSucceeds(
    mealsRef.doc("meal_1").update({
      calories: 350,
      updatedAt: updated,
    })
  );

  // Reject @DocumentId `id` field inside body
  await assertFails(
    mealsRef.doc("meal_bad_id").set(validMealPayload(ALICE_UID, created, { id: "meal_bad_id" }))
  );

  // Reject invalid category enum
  await assertFails(
    mealsRef.doc("meal_bad_cat").set(validMealPayload(ALICE_UID, created, { category: "BRUNCH" }))
  );

  // Reject negative calories
  await assertFails(
    mealsRef.doc("meal_neg").set(validMealPayload(ALICE_UID, created, { calories: -50 }))
  );

  // Owner delete succeeds
  await assertSucceeds(mealsRef.doc("meal_1").delete());
});

test("DailyHydration: owner create, update, invalid dateKey/bounds rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const updated = new Date(Date.now() - 1000);
  const hydRef = aliceDb.collection("users").doc(ALICE_UID).collection("hydration");

  await assertSucceeds(
    hydRef.doc(TODAY_KEY).set(validHydrationPayload(ALICE_UID, TODAY_KEY, 3, created))
  );

  await assertSucceeds(
    hydRef.doc(TODAY_KEY).update({
      glasses: 5,
      updatedAt: updated,
    })
  );

  // Reject glasses > 16
  await assertFails(
    hydRef.doc(TODAY_KEY).update({
      glasses: 25,
      updatedAt: updated,
    })
  );

  // Reject mismatched dateKey document ID
  await assertFails(
    hydRef.doc("2026-11-26").set(validHydrationPayload(ALICE_UID, TODAY_KEY, 2, created))
  );
});

test("Cross-user isolation: Alice cannot read or write Bob's profile, meals, or hydration", async () => {
  const ts = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("users").doc(BOB_UID).set(validProfilePayload(BOB_UID, ts));
    await adminDb
      .collection("users")
      .doc(BOB_UID)
      .collection("meals")
      .doc("bob_meal")
      .set(validMealPayload(BOB_UID, ts));
    await adminDb
      .collection("users")
      .doc(BOB_UID)
      .collection("hydration")
      .doc(TODAY_KEY)
      .set(validHydrationPayload(BOB_UID, TODAY_KEY, 4, ts));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).update({ displayName: "Hacked", updatedAt: new Date(Date.now() - 1000) }));
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("meals").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("meals").doc("bob_meal").delete());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("hydration").doc(TODAY_KEY).get());
});
