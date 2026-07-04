import { Routes, Route, useLocation } from 'react-router-dom'
import { useEffect } from 'react'
import Shell from './components/Shell'
import Home from './pages/Home'
import DexGrid from './pages/DexGrid'
import CreatureDetail from './pages/CreatureDetail'
import Maps from './pages/Maps'
import MapDetail from './pages/MapDetail'
import Story from './pages/Story'
import SceneDetail from './pages/SceneDetail'
import Moves from './pages/Moves'
import MoveDetail from './pages/MoveDetail'
import Items from './pages/Items'
import ItemDetail from './pages/ItemDetail'
import Trainers from './pages/Trainers'
import TrainerDetail from './pages/TrainerDetail'
import TypesPage from './pages/TypesPage'
import Bosses from './pages/Bosses'
import BossDetail from './pages/BossDetail'
import Cards from './pages/Cards'
import CardDetail from './pages/CardDetail'
import Furniture from './pages/Furniture'
import FurnitureDetail from './pages/FurnitureDetail'
import Camps from './pages/Camps'
import CampDetail from './pages/CampDetail'
import Squadrons from './pages/Squadrons'
import SquadronDetail from './pages/SquadronDetail'
import Quests from './pages/Quests'
import QuestDetail from './pages/QuestDetail'
import Achievements from './pages/Achievements'
import Tutorials from './pages/Tutorials'
import TutorialDetail from './pages/TutorialDetail'
import Mechanics from './pages/Mechanics'
import LogicGraphs from './pages/LogicGraphs'
import Quirks from './pages/Quirks'
import About from './pages/About'
import Placeholder from './pages/Placeholder'

/** Scroll to top on route change (predictable back/forward behavior). */
function ScrollReset() {
  const { pathname } = useLocation()
  useEffect(() => window.scrollTo(0, 0), [pathname])
  return null
}

export default function App() {
  return (
    <Shell>
      <ScrollReset />
      <Routes>
        <Route path="/" element={<Home />} />

        <Route path="/dex" element={<DexGrid />} />
        <Route path="/dex/:guid" element={<CreatureDetail />} />
        <Route path="/bosses" element={<Bosses />} />
        <Route path="/bosses/:guid" element={<BossDetail />} />
        <Route path="/types" element={<TypesPage />} />
        <Route path="/quirks" element={<Quirks />} />

        <Route path="/maps" element={<Maps />} />
        <Route path="/maps/:guid" element={<MapDetail />} />
        <Route path="/story" element={<Story />} />
        <Route path="/story/scene" element={<SceneDetail />} />
        <Route path="/quests" element={<Quests />} />
        <Route path="/quests/:guid" element={<QuestDetail />} />

        <Route path="/moves" element={<Moves />} />
        <Route path="/moves/:guid" element={<MoveDetail />} />
        <Route path="/items" element={<Items />} />
        <Route path="/items/:guid" element={<ItemDetail />} />
        <Route path="/cards" element={<Cards />} />
        <Route path="/cards/:guid" element={<CardDetail />} />
        <Route path="/furniture" element={<Furniture />} />
        <Route path="/furniture/:guid" element={<FurnitureDetail />} />
        <Route path="/mechanics" element={<Mechanics />} />

        <Route path="/trainers" element={<Trainers />} />
        <Route path="/trainers/:guid" element={<TrainerDetail />} />
        <Route path="/camps" element={<Camps />} />
        <Route path="/camps/:guid" element={<CampDetail />} />
        <Route path="/squadrons" element={<Squadrons />} />
        <Route path="/squadrons/:guid" element={<SquadronDetail />} />

        <Route path="/achievements" element={<Achievements />} />
        <Route path="/tutorials" element={<Tutorials />} />
        <Route path="/tutorials/:guid" element={<TutorialDetail />} />
        <Route path="/logic" element={<LogicGraphs />} />
        <Route path="/about" element={<About />} />

        <Route path="*" element={<Placeholder title="Lost in the tall grass" notFound />} />
      </Routes>
    </Shell>
  )
}
