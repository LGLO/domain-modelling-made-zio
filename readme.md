# What is it?
A ZIO version of https://github.com/swlaschin/DomainModelingMadeFunctional

This is learning pet project. This is reason why number of test is low.

## ZLayer
I'm not using ZLayer everywhere, sometimes I create classes that have some dependency injected in the constructor
and only `live` method in service object that extracts these dependencies from layers. This is oppose to creating
anonymous service implementations.

## Dotty
Perhaps when ecosystem is all ready for opaque types and enums I'll update this project.

## Other
There are some simplifications in the orignal code, that are too naive, for example accepting order id on input without
checking for duplicates. I'm not fixing them.
