HappyEaster
===========
An Easter themed plugin featuring spawn egg drops, *The Killer Rabbit of Caerbannog*,
and a craftable *Holy Hand Grenade of Antioch*.

Zombies, skeletons, wither skeletons and creepers killed by grenades have a
configurable chance of dropping their heads.

Overworld hostile mobs that were not recently damaged by a player drop extra
novelty items (referred to as `regular` drops in `config.yml`) in addition to
their vanilla drops.  If those mobs are hurt by players within the last 5
seconds, they will also drop spawn eggs, referred to as `special` drops in
`config.yml`.


Holy Hand Grenade
-----------------
In the default configuration, the crafting recipe is as depicted below:

![Crafting Recipe](https://raw.github.com/totemo/HappyEaster/master/wiki/images/antioch.png)


Configuration
-------------
Many aspects of the plugin's behaviour can be configured through `config.yml`,
including the health of killer rabbits, the drops by mobs, the damage done by
the Holy Hand Grenade, and its crafting recipe.

Most aspects of the configuration are deemed to be self-explanatory, when read
in conjunction with the comments in Configuration.java. However, a couple of
points are worthy of note:

 * `killer_rabbit.chance` is the probability, in the range [0.0, 1.0], that a
   hostile mob in the world `world.name` will be replaced with a killer rabbit.
   Minecraft does not consider killer rabbits to be hostile mobs. Instead they
   contribute to the animal mob cap. Accordingly, `killer_rabbit.chance` should
   not be set to 1.0 (100% spawn chance), because the server will fill the world
   with infinite killer rabbits in an attempt to spawn hostile mobs up to the
   cap. To keep the killer rabbit population under control, `HappyEaster` runs
   a task to remove any killer rabbits whose age exceeds 5 minutes if a player
   has not changed their name.
 * The plugin imbues killer rabbits with configurable potion effects. The
   strength effect has no discernable impact on the amount of damage the mob
   does.


Commands
--------

 * `/happyeaster reload` - Reload the configuration.


Permissions
-----------

 * `happyeaster.admin` - Permission to run `/happyeaster reload`.

