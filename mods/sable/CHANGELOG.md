# Changelog

## 1.1.5

- Create saws collect fruit, pods, and additional addon drops through native storage and overflow callbacks, preserving loot rules and item components.
- Falling Dynamic Trees fruit and pods are harvested through their block loot instead of escaping collection.
- Mechanical saw breaking cracks render on the visible core of trunks wider than one block.

## 1.1.4

- Saw cuts play break particles from the recorded branch and leaf states after Dynamic Trees removes those blocks.
- A moving contraption saw drops a stalled cut when the bearing leaves the block it was cutting.

## 1.1.3

- Moving Create contraption drills fell standing Dynamic Trees into Sable bodies and route cut drops through Create's mounted storage and overflow handling.
- Fixed, contraption, and Aeronautics-mounted saws share grown-volume loot and radius-scaled speed through Create's tree queue and native drop callbacks.
- Saw cuts remove supported leaves as well as branches without creating Sable tree bodies.

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
