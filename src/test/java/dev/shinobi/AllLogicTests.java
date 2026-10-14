package dev.shinobi;

import java.io.IOException;

/** Runs every pure-logic test class. Exits non-zero (inside the called class) on any failure. */
public final class AllLogicTests {
    public static void main(String[] args) throws IOException {
        ChargeLogicTests.main(args);
        BloodlineLogicTests.main(args);
        MigrationTests.main(args);
        DodgeLogicTests.main(args);
        EyeStyleTests.main(args);
        AdminLogicTests.main(args);
        SusanooLogicTests.main(args);
        HeadgearLogicTests.main(args);
        CastRouteGuardTests.main(args);
        System.out.println("all logic tests passed");
    }
}
