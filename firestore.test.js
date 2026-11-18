const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_driver_1";
const BOB_UID = "bob_driver_2";

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

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(ALICE_UID).collection("vehicles").get());
  await assertFails(
    unauth.collection("users").doc(ALICE_UID).collection("vehicles").doc("v_1").set({
      userId: ALICE_UID,
      name: "Test Car",
      displacementLiters: 2.0,
      fuelType: "PETROL",
      redlineRpm: 6500,
      shiftLightRpm: 6000,
      fuelPricePerLiter: 58.0,
      odometerKm: 10000.0,
      engineHours: 250.0,
      preferredPresetId: "main",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Owner can create, list, update, and delete vehicle profile; cross-user and ghost fields rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);
  const vehRef = aliceDb.collection("users").doc(ALICE_UID).collection("vehicles").doc("veh_1");

  await assertSucceeds(
    vehRef.set({
      userId: ALICE_UID,
      name: "Prius Hybrid",
      vin: "WVWZZZ1KZ8W123456",
      displacementLiters: 1.8,
      fuelType: "HYBRID",
      redlineRpm: 5200,
      shiftLightRpm: 4800,
      fuelPricePerLiter: 64.5,
      odometerKm: 84200.5,
      engineHours: 1450.2,
      preferredPresetId: "track",
      curbWeightKg: 1380,
      createdAt: now,
      updatedAt: now,
    })
  );

  // List & Update
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("vehicles").get());
  await assertSucceeds(
    vehRef.update({
      odometerKm: 84255.0,
      engineHours: 1451.5,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject cross-user access
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("vehicles").doc("veh_1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("vehicles").doc("veh_1").update({
      name: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field & future timestamp & immutable createdAt overwrite
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("vehicles").doc("veh_bad").set({
      id: "veh_bad",
      userId: ALICE_UID,
      name: "Ghost",
      displacementLiters: 2.0,
      fuelType: "PETROL",
      redlineRpm: 6500,
      shiftLightRpm: 6000,
      fuelPricePerLiter: 58.0,
      odometerKm: 0.0,
      engineHours: 0.0,
      preferredPresetId: "main",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    vehRef.update({
      createdAt: new Date(Date.now() - 200),
      updatedAt: new Date(Date.now() - 200),
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("vehicles").doc("veh_future").set({
      userId: ALICE_UID,
      name: "Future",
      displacementLiters: 2.0,
      fuelType: "PETROL",
      redlineRpm: 6500,
      shiftLightRpm: 6000,
      fuelPricePerLiter: 58.0,
      odometerKm: 0.0,
      engineHours: 0.0,
      preferredPresetId: "main",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  await assertSucceeds(vehRef.delete());
});

test("Maintenance, CustomPid, and TripSummary enforce exact schema and owner isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 3000);

  const maintRef = aliceDb.collection("users").doc(ALICE_UID).collection("maintenance").doc("m_oil");
  await assertSucceeds(
    maintRef.set({
      userId: ALICE_UID,
      vehicleKey: "veh_1",
      title: "Engine Oil 5W-40",
      category: "OIL",
      intervalKm: 10000,
      intervalHours: 250,
      lastServiceOdometerKm: 80000.0,
      lastServiceEngineHours: 1400.0,
      lastServiceTimestampMs: Date.now() - 86400000,
      notes: "Motul 8100 X-cess",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("maintenance").get());

  const pidRef = aliceDb.collection("users").doc(ALICE_UID).collection("customPids").doc("pid_1");
  await assertSucceeds(
    pidRef.set({
      userId: ALICE_UID,
      name: "DSG Oil Temp",
      shortName: "DSG Oil",
      modeAndPid: "221E1C",
      header: "7E1",
      equation: "((A*256)+B)/10-40",
      unit: "°C",
      minValue: 0.0,
      maxValue: 140.0,
      decimals: 1,
      enabled: true,
      warningHigh: 115.0,
      alertsEnabled: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  const tripRef = aliceDb.collection("users").doc(ALICE_UID).collection("trips").doc("trip_1");
  await assertSucceeds(
    tripRef.set({
      userId: ALICE_UID,
      vehicleName: "Golf GTI",
      startedAtMs: Date.now() - 3600000,
      endedAtMs: Date.now() - 1000,
      distanceKm: 42.5,
      durationSec: 2100,
      fuelUsedLiters: 3.4,
      avgLPer100Km: 8.0,
      avgSpeedKmh: 72.8,
      maxSpeedKmh: 124.0,
      estimatedCost: 219.3,
      ecoScore: 92,
      peakHorsepower: 148.5,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("trips").doc("trip_bad_eco").set({
      userId: ALICE_UID,
      vehicleName: "Golf GTI",
      startedAtMs: Date.now() - 3600000,
      endedAtMs: Date.now() - 1000,
      distanceKm: 10.0,
      durationSec: 600,
      fuelUsedLiters: 1.0,
      avgSpeedKmh: 60.0,
      maxSpeedKmh: 90.0,
      estimatedCost: 60.0,
      ecoScore: 150,
      createdAt: now,
      updatedAt: now,
    })
  );
});
