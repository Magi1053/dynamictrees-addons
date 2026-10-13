# Changelog

## 1.1.2

- Moving Create saws mill Dynamic Trees into contraption storage, with excess drops ejected beside the saw.
- Contraption saw cutting time uses primitive-log hardness and scales with trunk radius, matching stationary saws.
- Saws mounted on Sable sublevels use Create's native drop callback for ejection and Simulated's connected-storage collection.
- Mechanical drills use Create's native drop callback for standing-tree cut drops and fallen-wood harvests, including Simulated's connected storage.

## 1.1.1

- A fallen tree drops at least one of its species' seeds when its leaves break or collide, and commonly more than one.
- Fallen branch ends show log rings instead of bark, and side bark follows the main axis.

## 1.1.0

- Fallen wood pays its grown volume as logs. Sticks and seeds wait until that branch or leaf breaks.
- Chop speed scales with trunk thickness. Create saws mill a cut into drops; drills fell that portion.
- Severed branches fall as separate bodies. Cores stay rigid; thin wood and leaves snap on impact.
- Fallen trunks float, slow in water, and no longer force a crawl or swim pose.
- Jade can show the logs a fallen chop will drop.

## 1.0.0

- Initial release: Dynamic Trees felling becomes a harvestable Sable SubLevel.
- Standing-tree chop uses primitive-log speed; wood yield is placed as individual log blocks matching Dynamic Trees volume loot.
