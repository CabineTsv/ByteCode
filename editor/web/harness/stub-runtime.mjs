// A universal stand-in: any property is another stub, and stubs can be called and constructed.
export function makeStub(name) {
  const target = function () {};
  return new Proxy(target, {
    get(_target, property) {
      if (property === "then") return undefined; // never look like a promise
      if (property === Symbol.toPrimitive) return () => name;
      if (property === "toString") return () => name;
      return makeStub(`${name}.${String(property)}`);
    },
    apply() {
      return makeStub(`${name}()`);
    },
    construct() {
      return makeStub(`new ${name}`);
    },
  });
}
