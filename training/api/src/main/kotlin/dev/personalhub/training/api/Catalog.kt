package dev.personalhub.training.api

import dev.personalhub.training.api.Category.*
import dev.personalhub.training.api.Equipment.*
import dev.personalhub.training.api.Metric.*
import dev.personalhub.training.api.Muscle.*

/**
 * Seed catalog v1 (GYM-01/02/03). Names are generic movement names; instructions are original short cues written for
 * this project. No third-party text, images or animations are bundled. IDs are stable: never reuse or renumber.
 */
object SeedCatalog {
    const val VERSION = 1
    private fun e(id: String, name: String, eq: Equipment, cat: Category, p: Set<Muscle>, s: Set<Muscle>, vararg steps: String,
                  aliases: List<String> = emptyList(), metric: Metric = WEIGHT_REPS) =
        Exercise("seed:$id", name, aliases, eq, metric, cat, p, s, steps.toList())

    val exercises: List<Exercise> = listOf(
        // Push
        e("bench_bb", "Bench Press", BARBELL, PUSH, setOf(CHEST), setOf(FRONT_DELTS, TRICEPS), "Lie with eyes under the bar, feet planted, shoulder blades pinched.", "Lower the bar to mid-chest with elbows about 45° from the body.", "Press up and slightly back until arms are straight.", aliases = listOf("flat bench", "bench")),
        e("incline_db", "Incline Dumbbell Press", DUMBBELL, PUSH, setOf(CHEST), setOf(FRONT_DELTS, TRICEPS), "Set the bench to about 30°.", "Lower the dumbbells beside the upper chest.", "Press up until they nearly meet over the shoulders."),
        e("db_bench", "Dumbbell Bench Press", DUMBBELL, PUSH, setOf(CHEST), setOf(FRONT_DELTS, TRICEPS), "Start with dumbbells over the chest, palms forward.", "Lower slowly to chest level.", "Press back up without clanking at the top."),
        e("ohp_bb", "Overhead Press", BARBELL, PUSH, setOf(FRONT_DELTS), setOf(SIDE_DELTS, TRICEPS, TRAPS), "Stand with the bar on the front of the shoulders, glutes tight.", "Press straight up, moving the head back then through.", "Lock out over mid-foot.", aliases = listOf("OHP", "military press")),
        e("db_shoulder", "Dumbbell Shoulder Press", DUMBBELL, PUSH, setOf(FRONT_DELTS), setOf(SIDE_DELTS, TRICEPS), "Sit tall with dumbbells at ear height.", "Press up until arms are straight.", "Lower under control to the start."),
        e("lateral_raise", "Lateral Raise", DUMBBELL, PUSH, setOf(SIDE_DELTS), setOf(TRAPS), "Hold light dumbbells at your sides, slight bend in the elbows.", "Raise out to shoulder height leading with the elbows.", "Lower slowly."),
        e("cable_fly", "Cable Fly", CABLE, PUSH, setOf(CHEST), setOf(FRONT_DELTS), "Set pulleys at chest height and step forward.", "Bring hands together in a wide arc.", "Return until you feel a stretch across the chest."),
        e("pec_deck", "Chest Fly Machine", MACHINE, PUSH, setOf(CHEST), setOf(FRONT_DELTS), "Adjust the seat so handles are at chest height.", "Squeeze the handles together.", "Open slowly to a comfortable stretch.", aliases = listOf("pec deck")),
        e("pushup", "Push-up", BODYWEIGHT, PUSH, setOf(CHEST), setOf(TRICEPS, FRONT_DELTS, ABS), "Hands slightly wider than shoulders, body in a straight line.", "Lower until the chest is near the floor.", "Push back up without sagging the hips.", metric = BODYWEIGHT_REPS),
        e("dips", "Dips", BODYWEIGHT, PUSH, setOf(TRICEPS, CHEST), setOf(FRONT_DELTS), "Support yourself on parallel bars with arms straight.", "Lower until the upper arms are about parallel to the floor.", "Press back up.", metric = BODYWEIGHT_REPS),
        e("tricep_pushdown", "Triceps Pushdown", CABLE, PUSH, setOf(TRICEPS), emptySet(), "Grip the rope or bar with elbows at your sides.", "Extend the arms fully, spreading the rope at the bottom.", "Return until forearms are just above parallel."),
        e("skullcrusher", "Lying Triceps Extension", BARBELL, PUSH, setOf(TRICEPS), emptySet(), "Lie on a bench holding the bar over the shoulders.", "Bend the elbows to lower the bar toward the forehead.", "Extend back to straight arms.", aliases = listOf("skull crusher")),
        e("overhead_ext", "Overhead Triceps Extension", DUMBBELL, PUSH, setOf(TRICEPS), emptySet(), "Hold one dumbbell overhead with both hands.", "Lower it behind the head keeping elbows pointing up.", "Extend back overhead."),
        e("machine_press", "Machine Chest Press", MACHINE, PUSH, setOf(CHEST), setOf(TRICEPS, FRONT_DELTS), "Set the seat so handles are at mid-chest.", "Press forward to near lockout.", "Return slowly."),
        // Pull
        e("deadlift", "Deadlift", BARBELL, PULL, setOf(LOWER_BACK, GLUTES, HAMSTRINGS), setOf(TRAPS, QUADS, FOREARMS, LATS), "Bar over mid-foot, hinge and grip just outside the legs.", "Brace, flatten the back and push the floor away.", "Stand tall, then return the bar along the legs."),
        e("pullup", "Pull-up", BODYWEIGHT, PULL, setOf(LATS), setOf(BICEPS, UPPER_BACK, FOREARMS), "Hang from the bar with an overhand grip.", "Pull until the chin clears the bar.", "Lower to a full hang.", metric = BODYWEIGHT_REPS),
        e("chinup", "Chin-up", BODYWEIGHT, PULL, setOf(LATS, BICEPS), setOf(UPPER_BACK), "Hang with palms facing you, shoulder-width.", "Pull the chest toward the bar.", "Lower with control.", metric = BODYWEIGHT_REPS),
        e("assisted_pullup", "Assisted Pull-up", MACHINE, PULL, setOf(LATS), setOf(BICEPS, UPPER_BACK), "Kneel on the pad; more assistance makes it easier.", "Pull up until the chin is over the handles.", "Lower to straight arms.", metric = ASSISTED_REPS),
        e("lat_pulldown", "Lat Pulldown", CABLE, PULL, setOf(LATS), setOf(BICEPS, UPPER_BACK), "Grip the bar wide and lock the thighs under the pads.", "Pull the bar to the upper chest, elbows down and back.", "Let it rise until the arms are straight."),
        e("bb_row", "Barbell Row", BARBELL, PULL, setOf(UPPER_BACK, LATS), setOf(BICEPS, REAR_DELTS, LOWER_BACK), "Hinge to about 45° with a flat back.", "Row the bar to the lower ribs.", "Lower until the arms are straight.", aliases = listOf("bent over row")),
        e("db_row", "One-arm Dumbbell Row", DUMBBELL, PULL, setOf(LATS, UPPER_BACK), setOf(BICEPS, REAR_DELTS), "Support one hand and knee on a bench.", "Row the dumbbell toward the hip.", "Lower to a full stretch."),
        e("cable_row", "Seated Cable Row", CABLE, PULL, setOf(UPPER_BACK, LATS), setOf(BICEPS, REAR_DELTS), "Sit tall with a slight knee bend.", "Pull the handle to the stomach, chest up.", "Reach forward without rounding."),
        e("face_pull", "Face Pull", CABLE, PULL, setOf(REAR_DELTS), setOf(TRAPS, UPPER_BACK), "Set the rope at face height.", "Pull toward the eyes, hands ending beside the ears.", "Return slowly."),
        e("shrug", "Shrug", DUMBBELL, PULL, setOf(TRAPS), setOf(FOREARMS), "Hold dumbbells at your sides.", "Lift the shoulders straight up toward the ears.", "Pause, then lower."),
        e("bb_curl", "Barbell Curl", BARBELL, PULL, setOf(BICEPS), setOf(FOREARMS), "Stand with elbows at your sides.", "Curl the bar up without swinging.", "Lower to straight arms."),
        e("db_curl", "Dumbbell Curl", DUMBBELL, PULL, setOf(BICEPS), setOf(FOREARMS), "Palms forward, elbows still.", "Curl one or both dumbbells up.", "Lower slowly."),
        e("hammer_curl", "Hammer Curl", DUMBBELL, PULL, setOf(BICEPS, FOREARMS), emptySet(), "Hold dumbbells with palms facing each other.", "Curl up keeping that neutral grip.", "Lower with control."),
        e("rear_delt_fly", "Reverse Fly", DUMBBELL, PULL, setOf(REAR_DELTS), setOf(UPPER_BACK), "Hinge forward with light dumbbells.", "Raise the arms out to the sides.", "Lower slowly."),
        // Legs
        e("squat_bb", "Back Squat", BARBELL, LEGS, setOf(QUADS, GLUTES), setOf(ADDUCTORS, LOWER_BACK), "Bar on the upper back, feet shoulder-width.", "Sit down between the hips until thighs are at least parallel.", "Drive up keeping the chest proud.", aliases = listOf("squat")),
        e("front_squat", "Front Squat", BARBELL, LEGS, setOf(QUADS), setOf(GLUTES, ABS), "Bar resting on the front shoulders, elbows high.", "Squat down staying upright.", "Stand back up."),
        e("goblet_squat", "Goblet Squat", DUMBBELL, LEGS, setOf(QUADS, GLUTES), setOf(ABS), "Hold one dumbbell at the chest.", "Squat between the knees.", "Stand tall."),
        e("leg_press", "Leg Press", MACHINE, LEGS, setOf(QUADS, GLUTES), setOf(ADDUCTORS), "Feet hip-width on the platform.", "Lower until knees reach about 90°.", "Press back without locking the knees hard."),
        e("rdl", "Romanian Deadlift", BARBELL, LEGS, setOf(HAMSTRINGS, GLUTES), setOf(LOWER_BACK), "Hold the bar at the hips, soft knees.", "Push the hips back, sliding the bar down the thighs.", "Stop at a hamstring stretch and stand up.", aliases = listOf("RDL")),
        e("lunge", "Walking Lunge", DUMBBELL, LEGS, setOf(QUADS, GLUTES), setOf(HAMSTRINGS, ADDUCTORS), "Hold dumbbells at your sides.", "Step forward and lower the back knee toward the floor.", "Drive through the front foot into the next step."),
        e("split_squat", "Bulgarian Split Squat", DUMBBELL, LEGS, setOf(QUADS, GLUTES), setOf(ADDUCTORS), "Rear foot on a bench behind you.", "Lower straight down on the front leg.", "Push back up."),
        e("leg_ext", "Leg Extension", MACHINE, LEGS, setOf(QUADS), emptySet(), "Pad just above the ankles.", "Straighten the knees fully.", "Lower slowly."),
        e("leg_curl", "Leg Curl", MACHINE, LEGS, setOf(HAMSTRINGS), setOf(CALVES), "Pad just above the heels.", "Curl the heels toward the glutes.", "Return under control."),
        e("hip_thrust", "Hip Thrust", BARBELL, LEGS, setOf(GLUTES), setOf(HAMSTRINGS), "Upper back on a bench, bar over the hips.", "Drive the hips up until the body is flat.", "Lower without touching down fully."),
        e("calf_raise", "Standing Calf Raise", MACHINE, LEGS, setOf(CALVES), emptySet(), "Balls of the feet on the edge.", "Rise as high as possible.", "Lower to a full stretch."),
        e("bw_squat", "Bodyweight Squat", BODYWEIGHT, LEGS, setOf(QUADS, GLUTES), emptySet(), "Feet shoulder-width, arms forward.", "Sit down and back.", "Stand up.", metric = BODYWEIGHT_REPS),
        // Core
        e("plank", "Plank", BODYWEIGHT, CORE, setOf(ABS), setOf(OBLIQUES, FRONT_DELTS), "Forearms down, body straight from head to heels.", "Brace as if about to be poked in the stomach.", "Hold for time.", metric = DURATION),
        e("hanging_leg_raise", "Hanging Leg Raise", BODYWEIGHT, CORE, setOf(ABS), setOf(FOREARMS), "Hang from a bar.", "Raise the legs to hip height or higher.", "Lower without swinging.", metric = BODYWEIGHT_REPS),
        e("cable_crunch", "Cable Crunch", CABLE, CORE, setOf(ABS), setOf(OBLIQUES), "Kneel holding the rope beside the head.", "Crunch the ribs toward the hips.", "Return slowly."),
        e("russian_twist", "Russian Twist", BODYWEIGHT, CORE, setOf(OBLIQUES), setOf(ABS), "Sit leaning back with feet raised.", "Rotate the torso side to side.", "Count each side as one rep.", metric = BODYWEIGHT_REPS),
        // Cardio
        e("run", "Running", OTHER, CARDIO, emptySet(), emptySet(), "Log the distance and time of your run.", metric = DISTANCE_DURATION),
        e("cycle", "Cycling", MACHINE, CARDIO, emptySet(), emptySet(), "Log distance and time on a bike.", metric = DISTANCE_DURATION),
    )
    val byId: Map<String, Exercise> = exercises.associateBy { it.id }
}
