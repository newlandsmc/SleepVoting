# SleepVoting
A plugin that allows players to vote on skipping the night.

## How it works:

If a player gets in a bed, that counts as a vote. They may also get out of the bed.

Once the number of votes reaches the threshold defined in the config (percentage), the night
will be skipped.

## Extra Features:

- Hooks into vanish, so vanished players aren't counted.
- Skipping the night uses the `sleep.skipping_night` minecraft translation.
- Resets a player's insomnia counter (phantoms won't attack if they sleep).
